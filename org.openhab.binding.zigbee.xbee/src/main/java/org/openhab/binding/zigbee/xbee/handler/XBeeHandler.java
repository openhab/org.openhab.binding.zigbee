/**
 * Copyright (c) 2014,2018 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.zigbee.xbee.handler;

import org.eclipse.smarthome.core.thing.Bridge;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeSerialPort;
import org.openhab.binding.zigbee.xbee.internal.XBeeConfiguration;
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
// @NonNullByDefault
public class XBeeHandler extends ZigBeeCoordinatorHandler {
    private final Logger logger = LoggerFactory.getLogger(XBeeHandler.class);

    public XBeeHandler(Bridge coordinator) {
        super(coordinator);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee XBee serial bridge handler.");

        // Call the parent to finish any global initialisation
        super.initialize();

        XBeeConfiguration config = getConfigAs(XBeeConfiguration.class);

        ZigBeePort serialPort = new ZigBeeSerialPort(config.zigbee_port, config.zigbee_baud,
                FlowControl.FLOWCONTROL_OUT_RTSCTS);
        final ZigBeeTransportTransmit dongle = new ZigBeeDongleXBee(serialPort);

        logger.debug("ZigBee XBee Coordinator opening Port:'{}' PAN:{}, EPAN:{}, Channel:{}", config.zigbee_port,
                Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

        TransportConfig transportConfig = new TransportConfig();

        startZigBee(dongle, transportConfig, DefaultSerializer.class, DefaultDeserializer.class);
    }

}
