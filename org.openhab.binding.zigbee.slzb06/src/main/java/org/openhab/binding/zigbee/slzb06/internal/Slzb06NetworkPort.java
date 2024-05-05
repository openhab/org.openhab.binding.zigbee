/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.zigbee.slzb06.internal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.transport.ZigBeePort;

/**
 * The network implementation of Java serial port.
 *
 * @author Chris Jackson
 */
public class Slzb06NetworkPort implements ZigBeePort {
    /**
     * The logger.
     */
    private final Logger logger = LoggerFactory.getLogger(Slzb06NetworkPort.class);

    /**
     * The socket
     */
    Socket socket;

    /**
     * The serial port input stream.
     */
    private DataInputStream dataIn;

    /**
     * The serial port output stream.
     */
    private DataOutputStream dataOut;

    /**
     * The server identifier.
     */
    private final String serverName;

    /**
     * The server port.
     */
    private final int serverPort;

    /**
     * The length of the receive buffer
     */
    private static final int RX_BUFFER_LEN = 512;

    /**
     * The circular FIFO queue for receive data
     */
    private final int[] buffer = new int[RX_BUFFER_LEN];

    /**
     * The receive buffer end pointer (where we put the newly received data)
     */
    private int end = 0;

    /**
     * The receive buffer start pointer (where we take the data to pass to the application)
     */
    private int start = 0;

    /**
     * Synchronisation object for buffer queue manipulation
     */
    private final Object bufferSynchronisationObject = new Object();

    private ReceiveThread receiveThread;

    private volatile boolean running = true;

    /**
     * Constructor setting port name and baud rate.
     *
     * @param serverName the server name
     * @param serverPort the server port
     */
    public Slzb06NetworkPort(String serverName, int serverPort) {
        this.serverName = serverName;
        this.serverPort = serverPort;
    }

    @Override
    public boolean open(int baudRate, FlowControl flowControl) {
        return open();
    }

    @Override
    public boolean open(int baudRate) {
        return open();
    }

    @Override
    public boolean open() {
        try {
            logger.debug("Connecting to network port [{}:{}]", serverName, serverPort);

            Socket localSocket = new Socket();
            localSocket.connect(new InetSocketAddress(serverName, serverPort), 1000);

            dataIn = new DataInputStream(localSocket.getInputStream());
            dataOut = new DataOutputStream(localSocket.getOutputStream());

            logger.debug("Network port [{}] is initialized.", serverName);

            socket = localSocket;

            receiveThread = new ReceiveThread();
            receiveThread.start();

            return true;
        } catch (RuntimeException | IOException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            pw.close();
            logger.error("Network Error: Device cannot be opened on [{}:{}]. Caused by {}, call stack: {}", serverName,
                    serverPort, e.getMessage(), sw.toString());
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (socket != null) {
                running = false;

                try {
                    receiveThread.join();
                } catch (InterruptedException e) {
                    // Eatme!
                }

                dataIn.close();
                dataOut.close();
                socket.close();

                synchronized (this) {
                    this.notify();
                }

                socket = null;
                dataIn = null;
                dataOut = null;

                logger.debug("Network port '{}' closed.", serverName);
            }
        } catch (Exception e) {
            logger.error("Error closing network port: '{}' ", serverName, e);
        }
    }

    @Override
    public void write(int value) {
        logger.debug("SLZB06 '{}': Writing {} bytes", serverName, 1);
        if (dataOut == null) {
            logger.error("SLZB06 '{}': Write failed, dataOut is null", serverName);
            return;
        }
        try {
            dataOut.write(value);
            dataOut.flush();
        } catch (IOException e) {
            logger.error("SLZB06 '{}': Write failed, IO Exception {}", serverName, e.getMessage());
        }
    }

    @Override
    public void write(int[] outArray) {
        if (dataOut == null) {
            logger.error("SLZB06 '{}': Write failed, dataOut is null", serverName);

            return;
        }
        byte[] bytes = new byte[outArray.length];
        int cnt = 0;
        for (int value : outArray) {
            bytes[cnt++] = (byte) value;
        }
        try {
            dataOut.write(bytes);
            dataOut.flush();
        } catch (IOException e) {
            logger.error("SLZB06 '{}': Write failed, IO Exception {}", serverName, e.getMessage());
        }
    }

    @Override
    public int read() {
        return read(9999999);
    }

    @Override
    public int read(int timeout) {
        long endTime = System.currentTimeMillis() + timeout;

        try {
            while (System.currentTimeMillis() < endTime) {
                synchronized (bufferSynchronisationObject) {
                    if (start != end) {
                        int value = buffer[start++];
                        if (start >= RX_BUFFER_LEN) {
                            start = 0;
                        }
                        return value;
                    }
                }

                synchronized (this) {
                    if (socket == null) {
                        return -1;
                    }

                    wait(endTime - System.currentTimeMillis());
                }
            }
            return -1;
        } catch (InterruptedException e) {
        }
        return -1;
    }

    @Override
    public void purgeRxBuffer() {
        synchronized (bufferSynchronisationObject) {
            start = 0;
            end = 0;
        }
    }

    private class ReceiveThread extends Thread {
        @Override
        public void run() {
            logger.debug("SLZB06: ReceiveThread started");
            try {
                byte[] dataChunk = new byte[1024];
                int bytesRead;
                while (running && (bytesRead = dataIn.read(dataChunk)) != -1) {
                    processReceivedData(dataChunk, bytesRead);
                }
            } catch (Exception e) {
                logger.error("SLZB06: Error in ReceiveThread: {}", e.getMessage());
            }
            logger.debug("SLZB06: ReceiveThread closed");
        }
    }

    private void processReceivedData(byte[] dataChunk, int bytesRead) {
        synchronized (bufferSynchronisationObject) {
            for (int i = 0; i < bytesRead; i++) {
                buffer[end++] = dataChunk[i] & 0xff;
                if (end >= RX_BUFFER_LEN) {
                    end = 0;
                }
                if (end == start) {
                    logger.warn("Processing received data event: Serial buffer overrun");
                    if (++start == RX_BUFFER_LEN) {
                        start = 0;
                    }
                }
            }
        }

        synchronized (this) {
            this.notify();
        }
    }
}
