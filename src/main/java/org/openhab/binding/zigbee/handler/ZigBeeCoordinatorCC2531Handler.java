/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.math.BigDecimal;

import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.internal.ZigBeeSerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.ZigBeePort;

/**
 * The {@link ZigBeeCoordinatorCC2531Handler} is responsible for handling
 * commands, which are sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeCoordinatorCC2531Handler extends ZigBeeCoordinatorHandler {
    private Logger logger = LoggerFactory.getLogger(ZigBeeCoordinatorCC2531Handler.class);

    private final int DEFAULT_BAUD = 115200;

    private String portId;
    private int portBaud;

    private int magicNumber = 0xef;

    public ZigBeeCoordinatorCC2531Handler(Bridge coordinator, TranslationProvider translationProvider) {
        super(coordinator, translationProvider);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Not required - yet!
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee ZNP serial bridge handler.");

        // Call the parent to finish any global initialisation
        super.initialize();

        if (getConfig().get(ZigBeeBindingConstants.CONFIGURATION_ZNP_MAGICNUMBER) != null) {
            magicNumber = ((BigDecimal) getConfig().get(ZigBeeBindingConstants.CONFIGURATION_ZNP_MAGICNUMBER))
                    .intValue();
        }

        if (getConfig().get(ZigBeeBindingConstants.CONFIGURATION_BAUD) != null) {
            portBaud = ((BigDecimal) getConfig().get(ZigBeeBindingConstants.CONFIGURATION_BAUD)).intValue();
        } else {
            portBaud = DEFAULT_BAUD;
        }

        portId = (String) getConfig().get(ZigBeeBindingConstants.CONFIGURATION_PORT);
        ZigBeePort serialPort = new ZigBeeSerialPort(portId, portBaud, false);
        final ZigBeeDongleTiCc2531 dongle = new ZigBeeDongleTiCc2531(serialPort);

        dongle.setMagicNumber(magicNumber);

        logger.debug("ZigBee Coordinator ZNP opening Port:'{}' PAN:{}, Channel:{}", portId, Integer.toHexString(panId),
                Integer.toString(channelId));

        startZigBee(dongle, DefaultSerializer.class, DefaultDeserializer.class);
    }
}
