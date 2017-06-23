/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;

import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

/**
 * The {@link ZigBeeCoordinatorEmberHandler} is responsible for handling
 * commands, which are sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeCoordinatorEmberHandler extends ZigBeeCoordinatorHandler
        implements ZigBeePort, SerialPortEventListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeCoordinatorEmberHandler.class);

    private String portId;

    // The serial port.
    private SerialPort serialPort;

    // The serial port input stream.
    private InputStream inputStream;

    // The serial port output stream.
    private OutputStream outputStream;

    public ZigBeeCoordinatorEmberHandler(Bridge coordinator, TranslationProvider translationProvider) {
        super(coordinator, translationProvider);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Not required - yet!
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee Ember serial bridge handler.");

        // Call the parent to finish any global initialisation
        super.initialize();

        portId = (String) getConfig().get(ZigBeeBindingConstants.CONFIGURATION_PORT);
        final ZigBeeTransportTransmit dongle = new ZigBeeDongleEzsp(this);

        logger.debug("ZigBee Coordinator Ember opening Port:'{}' PAN:{}, EPAN:{}, Channel:{}", portId,
                Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

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

    private void openSerialPort(final String serialPortName, int baudRate) {
        logger.info("Connecting to serial port [{}]", serialPortName);
        try {
            CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(serialPortName);
            CommPort commPort = portIdentifier.open("org.openhab.binding.zigbee", 2000);
            serialPort = (gnu.io.SerialPort) commPort;
            serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                    gnu.io.SerialPort.PARITY_NONE);
            serialPort.setFlowControlMode(gnu.io.SerialPort.FLOWCONTROL_RTSCTS_OUT);

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
        } catch (IOException e) {
            logger.error("Error getting serial streams", e);
        }

        return;
    }

    @Override
    public boolean open() {
        try {
            openSerialPort(portId, 115200);
            return true;
        } catch (Exception e) {
            logger.error("Error...", e);
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
