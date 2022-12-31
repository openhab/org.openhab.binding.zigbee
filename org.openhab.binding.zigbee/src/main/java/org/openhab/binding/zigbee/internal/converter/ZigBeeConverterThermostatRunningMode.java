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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the thermostat running mode channel. This is a read-only channel the presents the current state of the
 * thermostat.
 * <p>
 * ThermostatRunningMode represents the running mode of the thermostat. The thermostat running mode can
 * only be Off, Cool or Heat. This attribute is intended to provide additional information when the thermostat’s
 * system mode is in auto mode. The attribute value is maintained to have the same value as the SystemMode
 * attribute.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterThermostatRunningMode extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterThermostatRunningMode.class);

    private ZclThermostatCluster cluster;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclThermostatCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        ZclThermostatCluster serverCluster = (ZclThermostatCluster) endpoint
                .getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
            pollingPeriod = POLLING_PERIOD_HIGH;
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE);
                CommandResult reportingResponse = attribute
                        .setReporting(REPORTING_PERIOD_DEFAULT_MIN, REPORTING_PERIOD_DEFAULT_MAX).get();
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
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
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
        cluster.readAttribute(ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclThermostatCluster cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.trace("{}: Thermostat cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        // Try to read the setpoint attribute
        ZclAttribute attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE);
        Object value = attribute.readValue(Long.MAX_VALUE);
        if (value == null) {
            logger.trace("{}: Thermostat running mode returned null", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint,
                        ZigBeeBindingConstants.CHANNEL_NAME_THERMOSTAT_RUNNINGMODE),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_RUNNINGMODE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_THERMOSTAT_RUNNINGMODE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE) {
            Integer value = (Integer) val;
            updateChannelState(new DecimalType(value));
        }
    }
}
