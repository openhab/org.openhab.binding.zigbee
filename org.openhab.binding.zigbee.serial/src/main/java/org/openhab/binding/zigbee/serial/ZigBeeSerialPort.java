/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.TooManyListenersException;
import java.util.stream.Stream;

import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortEvent;
import org.openhab.core.io.transport.serial.SerialPortEventListener;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.transport.ZigBeePort;

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
     * The serial port manager.
     */
    private SerialPortManager serialPortManager;

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
    public ZigBeeSerialPort(SerialPortManager serialPortManager, String portName, int baudRate,
            FlowControl flowControl) {
        this.serialPortManager = serialPortManager;
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

            // In some rare cases we have to check whether a port really exists, because if it doesn't the call to
            // CommPortIdentifier#open will kill the whole JVM.
            // Virtual ports (like RFC2217) do not have a discovery logic, so we have to skip this check.
            // TODO: Remove this check once nrjavaserial does no longer crash on non-existent ports.
            if(!portName.toLowerCase().startsWith("rfc2217")) {
                Stream<SerialPortIdentifier> serialPortIdentifiers = serialPortManager.getIdentifiers();
                if (!serialPortIdentifiers.findAny().isPresent()) {
                    logger.debug("No communication ports found, cannot connect to [{}]", portName);
                    return false;
                }
            }
            SerialPortIdentifier portIdentifier = serialPortManager.getIdentifier(portName);
            if (portIdentifier == null) {
                logger.error("Serial Error: Port [{}] does not exist.", portName);
                return false;
            }

            SerialPort localSerialPort;

            try {
                localSerialPort = portIdentifier.open("org.openhab.binding.zigbee", 100);
            } catch (PortInUseException e) {
                logger.error("Serial Error: Port [{}] is in use.", portName);
                return false;
            }

            try {
                localSerialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            } catch (UnsupportedCommOperationException e) {
                logger.error("Failed to set serial port parameters on [{}]", portName);
                return false;
            }

            try {
                switch (flowControl) {
                    case FLOWCONTROL_OUT_NONE:
                        localSerialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
                        break;
                    case FLOWCONTROL_OUT_RTSCTS:
                        localSerialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_OUT);
                        break;
                    case FLOWCONTROL_OUT_XONOFF:
                        localSerialPort.setFlowControlMode(SerialPort.FLOWCONTROL_XONXOFF_OUT);
                        break;
                    default:
                        break;
                }
            } catch (UnsupportedCommOperationException e) {
                logger.debug("Flow Control Mode {} is unsupported on [{}].", flowControl, portName);
            }

            try {
                localSerialPort.enableReceiveTimeout(100);
            } catch (UnsupportedCommOperationException e) {
                logger.debug("Enabling receive timeout is unsupported on [{}]", portName);
            }

            try {
                inputStream = localSerialPort.getInputStream();
            } catch (IOException e) {
                logger.debug("Failed to get input stream on [{}].", portName);
                return false;
            }

            try {
                outputStream = localSerialPort.getOutputStream();
            } catch (IOException e) {
                logger.debug("Failed to get output stream on [{}].", portName);
                return false;
            }

            try {
                localSerialPort.addEventListener(this);
            } catch (TooManyListenersException e) {
                logger.error("Serial Error: Too many listeners on [{}].", portName);
                return false;
            }

            localSerialPort.notifyOnDataAvailable(true);

            logger.debug("Serial port [{}] is initialized.", portName);

            serialPort = localSerialPort;
            return true;
        } catch (RuntimeException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            pw.close();
            logger.error("Serial Error: Device cannot be opened on [{}]. Caused by {}, call stack: {}", portName, e.getMessage(), sw.toString());
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
            outputStream.flush();
        } catch (IOException e) {
        }
    }

    @Override
    public void write(int[] outArray) {
        if (outputStream == null) {
            return;
        }
        byte[] bytes = new byte[outArray.length];
        int cnt = 0;
        for (int value : outArray) {
            bytes[cnt++] = (byte) value;
        }
        try {
            outputStream.write(bytes);
            outputStream.flush();
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
                            throw new IOException("Expected to be able to read " + available
                                    + " bytes, but saw error after " + offset);
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
