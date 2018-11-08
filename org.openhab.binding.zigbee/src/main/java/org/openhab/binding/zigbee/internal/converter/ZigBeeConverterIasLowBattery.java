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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;

/**
 * Converter for the IAS low battery indicator.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class ZigBeeConverterIasLowBattery extends ZigBeeConverterIas {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public boolean initializeConverter() {
        bitTest = CIE_BATTERY;
        return super.initializeConverter();
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (hasIasZoneInputCluster(endpoint)) {
            return ChannelBuilder
                    .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_IAS_LOWBATTERY),
                            ZigBeeBindingConstants.ITEM_TYPE_SWITCH)
                    .withType(ZigBeeBindingConstants.CHANNEL_IAS_LOWBATTERY)
                    .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_IAS_LOWBATTERY)
                    .withProperties(createProperties(endpoint)).build();
        } else {
            return null;
        }
    }

    private boolean hasIasZoneInputCluster(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID) == null) {
            logger.trace("{}: IAS zone cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        ZclIasZoneCluster cluster = (ZclIasZoneCluster) endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening IAS zone cluster", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }
}
