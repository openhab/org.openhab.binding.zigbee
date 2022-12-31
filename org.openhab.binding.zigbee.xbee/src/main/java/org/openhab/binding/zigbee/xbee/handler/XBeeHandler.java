/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.xbee.handler;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.serial.ZigBeeSerialPort;
import org.openhab.binding.zigbee.xbee.internal.XBeeConfiguration;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.thing.Bridge;
import org.osgi.service.component.annotations.Activate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.dongle.xbee.ZigBeeDongleXBee;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;

/**
 * The {@link XBeeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public class XBeeHandler extends ZigBeeCoordinatorHandler {
    private final Logger logger = LoggerFactory.getLogger(XBeeHandler.class);

    private final SerialPortManager serialPortManager;

    @Activate
    public XBeeHandler(Bridge coordinator, SerialPortManager serialPortManager,
            ZigBeeChannelConverterFactory channelFactory) {
        super(coordinator, channelFactory);
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void initializeDongle() {
        logger.debug("Initializing ZigBee XBee serial bridge handler.");

        XBeeConfiguration config = getConfigAs(XBeeConfiguration.class);
        ZigBeeTransportTransmit dongle = createDongle(config);
        TransportConfig transportConfig = new TransportConfig();

        startZigBee(dongle, transportConfig, DefaultSerializer.class, DefaultDeserializer.class);
    }

    private ZigBeeTransportTransmit createDongle(XBeeConfiguration config) {
        FlowControl flowControl = createFlowControl(config);
        ZigBeePort serialPort = new ZigBeeSerialPort(serialPortManager, config.zigbee_port, config.zigbee_baud,
                flowControl);
        ZigBeeTransportTransmit dongle = new ZigBeeDongleXBee(serialPort);

        logger.debug("ZigBee XBee Coordinator opening Port:'{}' PAN:{}, EPAN:{}, Channel:{}", config.zigbee_port,
                Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

        return dongle;
    }

    private FlowControl createFlowControl(XBeeConfiguration config) {
        FlowControl flowControl;
        if (ZigBeeBindingConstants.FLOWCONTROL_CONFIG_HARDWARE_CTSRTS.equals(config.zigbee_flowcontrol)) {
            flowControl = FlowControl.FLOWCONTROL_OUT_RTSCTS;
        } else if (ZigBeeBindingConstants.FLOWCONTROL_CONFIG_SOFTWARE_XONXOFF.equals(config.zigbee_flowcontrol)) {
            flowControl = FlowControl.FLOWCONTROL_OUT_XONOFF;
        } else {
            flowControl = FlowControl.FLOWCONTROL_OUT_NONE;
        }

        return flowControl;
    }

}
