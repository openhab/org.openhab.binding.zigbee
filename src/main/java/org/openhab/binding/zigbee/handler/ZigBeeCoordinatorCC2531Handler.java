/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import static org.openhab.binding.zigbee.ZigBeeBindingConstants.PARAMETER_PORT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeePort;
import com.zsmartsystems.zigbee.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

/**
 * The {@link ZigBeeCoordinatorCC2531Handler} is responsible for handling
 * commands, which are sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeCoordinatorCC2531Handler extends ZigBeeCoordinatorHandler
        implements ZigBeePort, SerialPortEventListener {
    private String portId;

    private Logger logger = LoggerFactory.getLogger(ZigBeeCoordinatorCC2531Handler.class);

    public ZigBeeCoordinatorCC2531Handler(Bridge coordinator) {
        super(coordinator);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Not required - yet!
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee CC2531EMK serial bridge handler.");

        // Call the parent to finish any global initialisation
        super.initialize();

        portId = (String) getConfig().get(PARAMETER_PORT);
        final ZigBeeTransportTransmit dongle = new ZigBeeDongleTiCc2531(this);

        logger.debug("ZigBee Coordinator CC2531 opening Port:'{}' PAN:{}, Channel:{}", portId,
                Integer.toHexString(panId), Integer.toString(channelId));

        startZigBee(dongle, DefaultSerializer.class, DefaultDeserializer.class);
    }

    @Override
    public void dispose() {
        close();
    }

    @Override
    public void thingUpdated(Thing thing) {
        super.thingUpdated(thing);
    }

    // @Override
    // protected void updateStatus(ThingStatus status, ThingStatusDetail detail, String desc) {
    // super.updateStatus(status, detail, desc);
    // for (Thing child : getThing().getThings()) {
    // child.setStatusInfo(new ThingStatusInfo(status, detail, desc));
    // }
    // }

    private void openSerialPort(final String serialPortName, int baudRate) {
        logger.debug("Connecting to serial port [{}]", serialPortName);
        try {
            CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(serialPortName);
            CommPort commPort = portIdentifier.open("org.openhab.binding.zigbee", 2000);
            serialPort = (gnu.io.SerialPort) commPort;
            serialPort.setSerialPortParams(baudRate, gnu.io.SerialPort.DATABITS_8, gnu.io.SerialPort.STOPBITS_1,
                    gnu.io.SerialPort.PARITY_NONE);
            ((CommPort) serialPort).enableReceiveThreshold(1);
            serialPort.enableReceiveTimeout(2000);

            // RXTX serial port library causes high CPU load
            // Start event listener, which will just sleep and slow down event loop
            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);

            logger.info("Serial port [{}] is initialized.", portId);
        } catch (NoSuchPortException e) {
            logger.error("Serial Error: Port {} does not exist", serialPortName);
            return;
        } catch (PortInUseException e) {
            logger.error("Serial Error: Port {} in use.", serialPortName);
            return;
        } catch (UnsupportedCommOperationException e) {
            logger.error("Serial Error: Unsupported comm operation on Port {}.", serialPortName);
            return;
        } catch (TooManyListenersException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        try {
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();

            // Write the 'magic byte'
            // Note that this might change in future, or with different dongles
            outputStream.write(0xef);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return;
    }

    // The serial port.
    private gnu.io.SerialPort serialPort;

    // The serial port input stream.
    private InputStream inputStream;

    // The serial port output stream.
    private OutputStream outputStream;

    @Override
    public boolean open() {
        try {
            openSerialPort(portId, 115200);
            return true;
        } catch (Exception e) {
            logger.error("Serial Open Error...", e);
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (serialPort != null) {
                serialPort.enableReceiveTimeout(1);

                inputStream.close();
                outputStream.flush();
                outputStream.close();

                serialPort.close();

                serialPort = null;
                inputStream = null;
                outputStream = null;

                logger.info("Serial port [{}] is closed.", portId);
            }
        } catch (Exception e) {
            // logger.warn("Error closing serial port: '" + serialPort.getName()
            // + "'", e);
        }
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void serialEvent(SerialPortEvent arg0) {
        try {
            logger.trace("RXTX library CPU load workaround, sleep forever");
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
        }
    }
}
