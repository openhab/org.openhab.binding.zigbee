/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the thermostat system mode channel
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterThermostatSystemMode extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterThermostatSystemMode.class);

    private ZclThermostatCluster cluster;

    @Override
    public boolean initializeDevice() {
        ZclThermostatCluster serverCluster = (ZclThermostatCluster) endpoint
                .getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_SYSTEMMODE);
                CommandResult reportingResponse = serverCluster
                        .setReporting(attribute, REPORTING_PERIOD_DEFAULT_MIN, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
            } else {
                logger.debug("{}: Failed to bind thermostat cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }

        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
            return false;
        }

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        cluster.getSystemMode(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclThermostatCluster cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.trace("{}: Thermostat cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        try {
            if (!cluster.discoverAttributes(false).get()) {
                // Device is not supporting attribute reporting - instead, just read the attributes
                Integer capabilities = cluster.getSystemMode(Long.MAX_VALUE);
                if (capabilities == null) {
                    logger.trace("{}: Thermostat system mode returned null", endpoint.getIeeeAddress());
                    return null;
                }
            } else if (!cluster.isAttributeSupported(ZclThermostatCluster.ATTR_SYSTEMMODE)) {
                logger.trace("{}: Thermostat system mode not supported", endpoint.getIeeeAddress());
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in thermostat cluster", endpoint.getIeeeAddress(), e);
        }

        // TODO: Define the allowable states
        cluster.getControlSequenceOfOperation(Long.MAX_VALUE);

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_THERMOSTAT_SYSTEMMODE),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_SYSTEMMODE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_THERMOSTAT_SYSTEMMODE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_SYSTEMMODE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                updateChannelState(new DecimalType(value));
            }
        }
    }
}
