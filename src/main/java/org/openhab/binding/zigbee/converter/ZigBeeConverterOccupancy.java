/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOccupancySensingCluster;

/**
 * Converter for the occupancy sensor.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterOccupancy extends ZigBeeChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterOccupancy.class);

    private ZclOccupancySensingCluster clusterOccupancy;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }
        logger.debug("{}: Initialising device occupancy cluster", device.getIeeeAddress());

        clusterOccupancy = (ZclOccupancySensingCluster) device.getCluster(ZclOccupancySensingCluster.CLUSTER_ID);
        if (clusterOccupancy == null) {
            logger.error("{}: Error opening occupancy cluster", device.getIeeeAddress());
            return;
        }

        clusterOccupancy.bind();

        // Add a listener, then request the status
        clusterOccupancy.addAttributeListener(this);
        clusterOccupancy.getOccupancy(0);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        clusterOccupancy.setOccupancyReporting(1, 600);
        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        logger.debug("{}: Closing device on/off cluster", device.getIeeeAddress());

        if (clusterOccupancy != null) {
            clusterOccupancy.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint device) {
        if (device.getCluster(ZclOccupancySensingCluster.CLUSTER_ID) == null) {
            return null;
        }
        return createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_OCCUPANCY_SENSOR,
                ZigBeeBindingConstants.ITEM_TYPE_SWITCH, "Occupancy");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", device.getIeeeAddress(), attribute);
        if (attribute.getId() == ZclOccupancySensingCluster.ATTR_OCCUPANCY) {
            Boolean value = (Boolean) attribute.getLastValue();
            if (value != null && value == true) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}
