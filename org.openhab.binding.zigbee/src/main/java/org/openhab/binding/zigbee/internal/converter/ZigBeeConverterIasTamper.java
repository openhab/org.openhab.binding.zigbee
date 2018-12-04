/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;

/**
 * Converter for the IAS tamper.
 *
 * @author Tommaso Travaglino - initial contribution
 */
public class ZigBeeConverterIasTamper extends ZigBeeConverterIas {

    @Override
    public boolean initializeConverter() {
        bitTest = CIE_TAMPER;
        return super.initializeConverter();
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (hasIasZoneInputCluster(endpoint)) {
            return ChannelBuilder
                    .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_IAS_TAMPER),
                            ZigBeeBindingConstants.ITEM_TYPE_SWITCH)
                    .withType(ZigBeeBindingConstants.CHANNEL_IAS_TAMPER)
                    .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_IAS_TAMPER)
                    .withProperties(createProperties(endpoint)).build();
        } else {
            return null;
        }
    }

}
