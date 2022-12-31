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

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.config.ZclReportingConfig;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Converter for the thermostat PI cooling demand channel. The PI Cooling Demand attribute specifies the level
 * of cooling currently demanded by the thermostat.
 *
 * @author Lucas Christian
 *
 */
public class ZigBeeConverterThermostatPiCoolingDemand extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterThermostatPiCoolingDemand.class);

    private static BigDecimal CHANGE_DEFAULT = new BigDecimal(1);
    private static BigDecimal CHANGE_MIN = new BigDecimal(1);
    private static BigDecimal CHANGE_MAX = new BigDecimal(100);

    private ZclThermostatCluster cluster;
    private ZclAttribute attribute;
    private ZclReportingConfig configReporting;

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
            return false;
        }

        ZclReportingConfig reporting = new ZclReportingConfig(channel);

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_PICOOLINGDEMAND);

                CommandResult reportingResponse = attribute.setReporting(reporting.getReportingTimeMin(),
                        reporting.getReportingTimeMax(), reporting.getReportingChange()).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, reporting.getPollingPeriod());
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

        attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_PICOOLINGDEMAND);
        if (attribute == null) {
            logger.error("{}: Error opening device thermostat PI cooling demand attribute", endpoint.getIeeeAddress());
            return false;
        }

        // Add reporting configuration
        configReporting = new ZclReportingConfig(channel);
        configReporting.setAnalogue(CHANGE_DEFAULT, CHANGE_MIN, CHANGE_MAX);
        configOptions = new ArrayList<>();
        configOptions.addAll(configReporting.getConfiguration());

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        cluster.removeAttributeListener(this);
    }

    @Override
    public int getPollingPeriod() {
        return configReporting.getPollingPeriod();
    }

    @Override
    public void handleRefresh() {
        attribute.readValue(0);
    }

    @Override
    public void updateConfiguration(@NonNull final Configuration currentConfiguration,
            final Map<String, Object> updatedParameters) {

        if (configReporting.updateConfiguration(currentConfiguration, updatedParameters)) {
            try {
                final ZclAttribute attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_PICOOLINGDEMAND);

                final CommandResult reportingResponse = attribute.setReporting(configReporting.getReportingTimeMin(),
                        configReporting.getReportingTimeMax(), configReporting.getReportingChange()).get();
                handleReportingResponse(reportingResponse, configReporting.getPollingPeriod(),
                        configReporting.getReportingTimeMax());
            } catch (InterruptedException | ExecutionException e) {
                logger.debug("{}: Thermostat PI cooling demand exception setting reporting", endpoint.getIeeeAddress(), e);
            }
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclThermostatCluster cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.trace("{}: Thermostat cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        // Try to read the setpoint attribute
        ZclAttribute attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_PICOOLINGDEMAND);
        Object value = attribute.readValue(Long.MAX_VALUE);
        if (value == null) {
            logger.trace("{}: Thermostat PI cooling demand returned null", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_THERMOSTAT_COOLING_DEMAND),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_COOLING_DEMAND)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_THERMOSTAT_COOLING_DEMAND)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_PICOOLINGDEMAND) {
            Integer value = (Integer) val;
            updateChannelState(valueToPercentDimensionless(value));
        }
    }
}
