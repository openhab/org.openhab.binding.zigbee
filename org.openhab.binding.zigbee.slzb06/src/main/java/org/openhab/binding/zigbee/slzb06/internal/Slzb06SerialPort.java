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

import org.openhab.binding.zigbee.slzb06.Slzb06BindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.transport.ZigBeePort;

/**
 * The default/reference Java serial port implementation using serial events to provide a non-blocking read call.
 *
 * @author Chris Jackson
 */
public class Slzb06SerialPort implements ZigBeePort {
    /**
     * The logger.
     */
    private final Logger logger = LoggerFactory.getLogger(Slzb06SerialPort.class);

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
     * The length of the receive buffer
     */
    private static final int RX_BUFFER_LEN = 512;

    /**
     * The circular fifo queue for receive data
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

    /**
     * Constructor setting port name and baud rate.
     *
     * @param portName the port name
     * @param baudRate the baud rate
     * @param flowControl to use flow control
     */
    public Slzb06SerialPort(String serverName) {
        this.serverName = serverName;
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
            logger.debug("Connecting to network port [{}]", serverName);

            Socket localSocket = new Socket();
            socket.connect(new InetSocketAddress(serverName, Slzb06BindingConstants.PORT), 1000);

            dataIn = new DataInputStream(localSocket.getInputStream());
            dataOut = new DataOutputStream(localSocket.getOutputStream());

            logger.debug("Network port [{}] is initialized.", serverName);

            socket = localSocket;
            return true;
        } catch (RuntimeException | IOException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            pw.close();
            logger.error("Network Error: Device cannot be opened on [{}]. Caused by {}, call stack: {}", serverName,
                    e.getMessage(), sw.toString());
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (socket != null) {
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
        if (dataOut == null) {
            return;
        }
        try {
            dataOut.write(value);
            dataOut.flush();
        } catch (IOException e) {
        }
    }

    @Override
    public void write(int[] outArray) {
        if (dataOut == null) {
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
}
