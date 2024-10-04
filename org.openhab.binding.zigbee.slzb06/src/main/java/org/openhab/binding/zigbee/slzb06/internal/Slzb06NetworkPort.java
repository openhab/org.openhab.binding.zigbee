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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
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
    private Socket socket;

    /**
     * The serial port input stream.
     */
    private BufferedInputStream dataIn;

    /**
     * The serial port output stream.
     */
    private BufferedOutputStream dataOut;

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
    protected static final int RX_BUFFER_LEN = 512;

    /**
     * The amount of time to wait for the receive thread to join when closing the port
     */
    private static final int THREAD_JOIN_TIMEOUT = 1000;

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
            logger.debug("SLZB06 Connecting to network port [{}:{}]", serverName, serverPort);

            Socket localSocket = new Socket();
            localSocket.connect(new InetSocketAddress(serverName, serverPort), 1000);

            dataIn = new BufferedInputStream(localSocket.getInputStream());
            dataOut = new BufferedOutputStream(localSocket.getOutputStream());

            logger.debug("SLZB06 '{}': Network port is initialized.", serverName);

            socket = localSocket;

            receiveThread = new ReceiveThread();
            receiveThread.start();

            return true;
        } catch (RuntimeException | IOException e) {
            logger.error("SLZB06 Network Error: Device cannot be opened on [{}:{}]. Caused by '{}'", serverName,
                    serverPort, e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        logger.debug("SLZB06 '{}': Network port closing", serverName);
        try {
            if (socket != null) {
                logger.debug("SLZB06 '{}': Network port closing socket", serverName);
                running = false;

                try {
                    receiveThread.join(THREAD_JOIN_TIMEOUT);
                } catch (InterruptedException e) {
                    // Eatme!
                }
                logger.debug("SLZB06 '{}': Network port closed - joined", serverName);

                dataOut.flush();

                dataIn.close();
                dataOut.close();
                socket.close();

                logger.debug("SLZB06 '{}': Network port closed - wait for sync", serverName);
                synchronized (this) {
                    this.notify();
                }

                socket = null;
                dataIn = null;
                dataOut = null;

                logger.debug("SLZB06 '{}': Network port closed.", serverName);
            }
        } catch (Exception e) {
            logger.error("SLZB06 '{}': Error closing network port ", serverName, e);
            socket = null;
            this.notify();
        }
    }

    @Override
    public void write(int value) {
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
        return read(Integer.MAX_VALUE);
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
            logger.debug("SLZB06 '{}': ReceiveThread started", serverName);
            try {
                byte[] dataChunk = new byte[1024];
                int bytesRead;
                while (running && (bytesRead = dataIn.read(dataChunk)) != -1) {
                    logger.trace("SLZB06: ReceiveThread received {} bytes", bytesRead);
                    processReceivedData(dataChunk, bytesRead);
                }
            } catch (Exception e) {
                logger.error("SLZB06 '{}': Error in ReceiveThread: {}", serverName, e.getMessage());
            }
            logger.debug("SLZB06 '{}': ReceiveThread closed", serverName);
        }
    }

    protected void processReceivedData(byte[] dataChunk, int bytesRead) {
        synchronized (bufferSynchronisationObject) {
            for (int i = 0; i < bytesRead; i++) {
                buffer[end++] = dataChunk[i] & 0xff;
                if (end >= RX_BUFFER_LEN) {
                    end = 0;
                }
                if (end == start) {
                    logger.warn(
                            "SLZB06 '{}': Processing received data event: Serial buffer overrun [{}:{}] with {}/{} bytes",
                            serverName, start, end, i, bytesRead);
                    if (++start == RX_BUFFER_LEN) {
                        start = 0;
                        end = 0;
                    }
                }
            }
        }

        synchronized (this) {
            this.notify();
        }
    }
}
