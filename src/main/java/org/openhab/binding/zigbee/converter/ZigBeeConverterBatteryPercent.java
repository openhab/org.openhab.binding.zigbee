/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the battery percent channel.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterBatteryPercent extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterBatteryPercent.class);

    private ZclPowerConfigurationCluster cluster;

    @Override
    public boolean initializeConverter() {
        logger.debug("{}: Initialising device battery percent converter", endpoint.getIeeeAddress());

        cluster = (ZclPowerConfigurationCluster) endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening power configuration cluster", endpoint.getIeeeAddress());
            return false;
        }

        cluster.bind();

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        // cluster.(0);

        // Configure reporting - no faster than once per ten minutes - no slower than every 2 hours.
        // cluster.setOccupancyReporting(600, 7200);
        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing power configuration cluster", endpoint.getIeeeAddress());

        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID) == null) {
            return null;
        }
        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_POWER_BATTERYPERCENT,
                ZigBeeBindingConstants.CHANNEL_POWER_BATTERYPERCENT, "Battery Percent");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.POWER_CONFIGURATION
                && attribute.getId() == ZclPowerConfigurationCluster.ATTR_BATTERYAHRRATING) {
            Integer value = (Integer) attribute.getLastValue();
            if (value == null) {
                return;
            }

            updateChannelState(new PercentType(value));
        }
    }
}
