/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.math.BigDecimal;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.internal.ZigBeeSerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;

/**
 * The {@link ZigBeeCoordinatorEmberHandler} is responsible for handling
 * commands, which are sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeCoordinatorEmberHandler extends ZigBeeCoordinatorHandler {
    private Logger logger = LoggerFactory.getLogger(ZigBeeCoordinatorEmberHandler.class);

    private final int DEFAULT_BAUD = 115200;

    private String portId;
    private int portBaud;

    public ZigBeeCoordinatorEmberHandler(@NonNull Bridge coordinator, TranslationProvider translationProvider) {
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

        if (getConfig().get(ZigBeeBindingConstants.CONFIGURATION_BAUD) != null) {
            portBaud = ((BigDecimal) getConfig().get(ZigBeeBindingConstants.CONFIGURATION_BAUD)).intValue();
        } else {
            portBaud = DEFAULT_BAUD;
        }
        ZigBeePort serialPort = new ZigBeeSerialPort(portId, portBaud, true);
        final ZigBeeTransportTransmit dongle = new ZigBeeDongleEzsp(serialPort);

        logger.debug("ZigBee Coordinator Ember opening Port:'{}' PAN:{}, EPAN:{}, Channel:{}", portId,
                Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

        updateStatus(ThingStatus.UNKNOWN);
        startZigBee(dongle, DefaultSerializer.class, DefaultDeserializer.class);
    }

}
