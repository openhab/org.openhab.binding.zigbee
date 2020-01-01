/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster.ATTR_BATTERYPERCENTAGEREMAINING;

import java.util.concurrent.ExecutionException;

import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
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
    public boolean initializeDevice() {
        logger.debug("{}: Initialising device battery percent converter", endpoint.getIeeeAddress());

        ZclPowerConfigurationCluster serverCluster = (ZclPowerConfigurationCluster) endpoint
                .getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening power configuration cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per ten minutes - no slower than every 2 hours.
                CommandResult reportingResponse = serverCluster
                        .setReporting(serverCluster.getAttribute(ATTR_BATTERYPERCENTAGEREMAINING), 600,
                                REPORTING_PERIOD_DEFAULT_MAX, 1)
                        .get();

                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
            } else {
                logger.error("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclPowerConfigurationCluster) endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening power configuration cluster", endpoint.getIeeeAddress());
            return false;
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
        cluster.getBatteryPercentageRemaining(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclPowerConfigurationCluster powerCluster = (ZclPowerConfigurationCluster) endpoint
                .getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (powerCluster == null) {
            logger.trace("{}: Power configuration cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        try {
            if (!powerCluster.discoverAttributes(false).get() && !powerCluster
                    .isAttributeSupported(ZclPowerConfigurationCluster.ATTR_BATTERYPERCENTAGEREMAINING)) {
                logger.trace("{}: Power configuration cluster battery percentage not supported",
                        endpoint.getIeeeAddress());

                return null;
            } else if (powerCluster.getBatteryPercentageRemaining(Long.MAX_VALUE) == null) {
                logger.trace("{}: Power configuration cluster battery percentage returned null",
                        endpoint.getIeeeAddress());
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in power configuration cluster",
                    endpoint.getIeeeAddress(), e);
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_POWER_BATTERYPERCENT),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_POWER_BATTERYPERCENT)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_POWER_BATTERYPERCENT)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.POWER_CONFIGURATION
                && attribute.getId() == ZclPowerConfigurationCluster.ATTR_BATTERYPERCENTAGEREMAINING) {
            Integer value = (Integer) val;

            updateChannelState(new DecimalType(value / 2));
        }
    }
}
