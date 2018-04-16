/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the battery voltage channel.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterBatteryVoltage extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterBatteryVoltage.class);

    private ZclPowerConfigurationCluster cluster;

    @Override
    public boolean initializeConverter() {
        logger.debug("{}: Initialising device battery voltage converter", endpoint.getIeeeAddress());

        cluster = (ZclPowerConfigurationCluster) endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening power configuration cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = cluster.bind().get();
            if (bindResponse.isSuccess()) {
                ZclAttribute attribute = cluster.getAttribute(ZclPowerConfigurationCluster.ATTR_BATTERYRATEDVOLTAGE);
                // Configure reporting - no faster than once per ten minutes - no slower than every 2 hours.
                cluster.setReporting(attribute, 600, 7200, 1).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }

        // Add a listener, then request the status
        cluster.addAttributeListener(this);

        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing power configuration cluster", endpoint.getIeeeAddress());

        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        cluster.getBatteryVoltage(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclPowerConfigurationCluster powerCluster = (ZclPowerConfigurationCluster) endpoint
                .getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (powerCluster == null) {
            return null;
        }

        try {
            if (!powerCluster.discoverAttributes(false).get()) {
                logger.debug("{}: Failed discovering attributes in power configuration cluster",
                        endpoint.getIeeeAddress());
                if (powerCluster.getBatteryVoltage(Long.MAX_VALUE) == null) {
                    return null;
                }
            } else if (!powerCluster.isAttributeSupported(ZclPowerConfigurationCluster.ATTR_BATTERYVOLTAGE)) {
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in power configuration cluster",
                    endpoint.getIeeeAddress(), e);
        }

        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_POWER_BATTERYVOLTAGE,
                ZigBeeBindingConstants.ITEM_TYPE_NUMBER, "Battery Voltage");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.POWER_CONFIGURATION
                && attribute.getId() == ZclPowerConfigurationCluster.ATTR_BATTERYVOLTAGE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value == null) {
                return;
            }
            updateChannelState(new DecimalType(BigDecimal.valueOf(value, 1)));
        }
    }
}
