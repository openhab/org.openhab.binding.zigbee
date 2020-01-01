/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Set;
import java.util.TooManyListenersException;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.transport.ZigBeePort;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

/**
 * The default/reference Java serial port implementation using serial events to provide a non-blocking read call.
 *
 * @author Chris Jackson
 */
public class ZigBeeSerialPort implements ZigBeePort, SerialPortEventListener {
    /**
     * The logger.
     */
    private final Logger logger = LoggerFactory.getLogger(ZigBeeSerialPort.class);

    /**
     * The portName portName.
     */
    private SerialPort serialPort;

    /**
     * The serial port input stream.
     */
    private InputStream inputStream;

    /**
     * The serial port output stream.
     */
    private OutputStream outputStream;

    /**
     * The port identifier.
     */
    private final String portName;

    /**
     * The baud rate.
     */
    private final int baudRate;

    /**
     * True to enable RTS / CTS flow control
     */
    private final FlowControl flowControl;

    /**
     * The length of the receive buffer
     */
    private final int RX_BUFFER_LEN = 512;

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

    private Set<String> portOpenRuntimeExcepionMessages = ConcurrentHashMap.newKeySet();

    /**
     * Constructor setting port name and baud rate.
     *
     * @param portName the port name
     * @param baudRate the baud rate
     * @param flowControl to use flow control
     */
    public ZigBeeSerialPort(String portName, int baudRate, FlowControl flowControl) {
        this.portName = portName;
        this.baudRate = baudRate;
        this.flowControl = flowControl;
    }

    @Override
    public boolean open() {
        return open(baudRate, flowControl);
    }

    @Override
    public boolean open(int baudRate) {
        return open(baudRate, flowControl);
    }

    @Override
    public boolean open(int baudRate, FlowControl flowControl) {
        try {
            logger.debug("Connecting to serial port [{}] at {} baud, flow control {}.", portName, baudRate,
                    flowControl);

            // in some rare cases we have to check whether a port really exists, because if it doesn't the call to
            // CommPortIdentifier#open will kill the whole JVM
            Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();
            if (!portList.hasMoreElements()) {
                logger.debug("No communication ports found, cannot connect to [{}]", portName);
                return false;
            }

            try {
                CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
                CommPort commPort = portIdentifier.open("org.openhab.binding.zigbee", 100);
                serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
                switch (flowControl) {
                    case FLOWCONTROL_OUT_NONE:
                        serialPort.setFlowControlMode(gnu.io.SerialPort.FLOWCONTROL_NONE);
                        break;
                    case FLOWCONTROL_OUT_RTSCTS:
                        serialPort.setFlowControlMode(gnu.io.SerialPort.FLOWCONTROL_RTSCTS_OUT);
                        break;
                    case FLOWCONTROL_OUT_XONOFF:
                        serialPort.setFlowControlMode(gnu.io.SerialPort.FLOWCONTROL_XONXOFF_OUT);
                        break;
                    default:
                        break;
                }

                serialPort.enableReceiveTimeout(100);
                serialPort.addEventListener(this);
                serialPort.notifyOnDataAvailable(true);

                logger.debug("Serial port [{}] is initialized.", portName);
                portOpenRuntimeExcepionMessages.clear();
            } catch (NoSuchPortException e) {
                logger.error("Serial Error: Port {} does not exist.", portName);
                return false;
            } catch (PortInUseException e) {
                logger.error("Serial Error: Port {} in use.", portName);
                return false;
            } catch (UnsupportedCommOperationException e) {
                logger.error("Serial Error: Unsupported comm operation on Port {}.", portName);
                return false;
            } catch (TooManyListenersException e) {
                logger.error("Serial Error: Too many listeners on Port {}.", portName);
                return false;
            } catch (RuntimeException e) {
                if (!portOpenRuntimeExcepionMessages.contains(e.getMessage())) {
                    portOpenRuntimeExcepionMessages.add(e.getMessage());
                    logger.error("Serial Error: Device cannot be opened on Port {}. Caused by {}", portName,
                            e.getMessage());
                }
                return false;
            }

            try {
                inputStream = serialPort.getInputStream();
                outputStream = serialPort.getOutputStream();
            } catch (IOException e) {
            }

            return true;
        } catch (Exception e) {
            logger.error("Unable to open serial port: ", e);
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (serialPort != null) {
                serialPort.removeEventListener();

                outputStream.flush();

                inputStream.close();
                outputStream.close();

                serialPort.close();

                serialPort = null;
                inputStream = null;
                outputStream = null;

                synchronized (this) {
                    this.notify();
                }

                logger.debug("Serial port '{}' closed.", portName);
            }
        } catch (Exception e) {
            logger.error("Error closing serial port: '{}' ", portName, e);
        }
    }

    @Override
    public void write(int value) {
        if (outputStream == null) {
            return;
        }
        try {
            outputStream.write(value);
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
                    if (serialPort == null) {
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
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
                synchronized (bufferSynchronisationObject) {
                    int available = inputStream.available();
                    logger.trace("Processing DATA_AVAILABLE event: have {} bytes available", available);
                    byte buf[] = new byte[available];
                    int offset = 0;
                    while (offset != available) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Processing DATA_AVAILABLE event: try read  {} at offset {}",
                                    available - offset, offset);
                        }
                        int n = inputStream.read(buf, offset, available - offset);
                        if (logger.isTraceEnabled()) {
                            logger.trace("Processing DATA_AVAILABLE event: did read {} of {} at offset {}", n,
                                    available - offset, offset);
                        }
                        if (n <= 0) {
                            throw new IOException(
                                    "Expected to be able to read " + available + " bytes, but saw error after "
                                            + offset);
                        }
                        offset += n;
                    }
                    for (int i = 0; i < available; i++) {
                        buffer[end++] = buf[i] & 0xff;
                        if (end >= RX_BUFFER_LEN) {
                            end = 0;
                        }
                        if (end == start) {
                            logger.warn("Processing DATA_AVAILABLE event: Serial buffer overrun");
                            if (++start == RX_BUFFER_LEN) {
                                start = 0;
                            }

                        }
                    }
                }
            } catch (IOException e) {
                logger.warn("Processing DATA_AVAILABLE event: received IOException in serial port event", e);
            }

            synchronized (this) {
                this.notify();
            }
        }
    }

    @Override
    public void purgeRxBuffer() {
        synchronized (bufferSynchronisationObject) {
            start = 0;
            end = 0;
        }
    }

}
