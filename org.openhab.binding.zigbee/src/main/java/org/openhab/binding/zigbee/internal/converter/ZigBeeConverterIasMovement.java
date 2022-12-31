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
package org.openhab.binding.zigbee.internal.converter;

import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;

/**
 * Converter for the IAS movement sensor.
 *
 * @author Henning Sudbrock - Initial Contribution
 */
public class ZigBeeConverterIasMovement extends ZigBeeConverterIas {

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        bitTest = CIE_ALARM1;
        return super.initializeConverter(thing);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (!supportsIasChannel(endpoint, ZoneTypeEnum.VIBRATION_MOVEMENT_SENSOR)) {
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_IAS_MOVEMENTSENSOR),
                        ZigBeeBindingConstants.ITEM_TYPE_SWITCH)
                .withType(ZigBeeBindingConstants.CHANNEL_IAS_MOVEMENTSENSOR)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_IAS_MOVEMENTSENSOR)
                .withProperties(createProperties(endpoint)).build();
    }
}
