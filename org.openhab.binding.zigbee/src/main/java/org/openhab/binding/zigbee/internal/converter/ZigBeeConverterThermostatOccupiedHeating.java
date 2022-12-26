/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the thermostat occupied heating setpoint channel
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterThermostatOccupiedHeating extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterThermostatOccupiedHeating.class);

    private ZclThermostatCluster cluster;
    private ZclAttribute attribute;

    private boolean isServer;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclThermostatCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.singleton(ZclThermostatCluster.CLUSTER_ID);
    }

    @Override
    public boolean initializeDevice() {
        ZclThermostatCluster localCluster;

        localCluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (localCluster != null) {
            return initialiseDeviceServer(localCluster);
        }

        localCluster = (ZclThermostatCluster) endpoint.getOutputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (localCluster != null) {
            return initialiseDeviceClient(localCluster);
        }

        logger.error("{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
        return false;
    }

    private boolean initialiseDeviceServer(ZclThermostatCluster serverCluster) {
        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_OCCUPIEDHEATINGSETPOINT);
                CommandResult reportingResponse = attribute
                        .setReporting(REPORTING_PERIOD_DEFAULT_MIN, REPORTING_PERIOD_DEFAULT_MAX, 10).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
            } else {
                logger.debug("{}: Failed to bind thermostat cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }
        return true;
    }

    private boolean initialiseDeviceClient(ZclThermostatCluster clientCluster) {
        try {
            CommandResult bindResponse = bind(clientCluster).get();
            if (!bindResponse.isSuccess()) {
                logger.error("{}: Error 0x{} setting client binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting binding ", endpoint.getIeeeAddress(), e);
        }

        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster != null) {
            isServer = true;
        } else {
            cluster = (ZclThermostatCluster) endpoint.getOutputCluster(ZclThermostatCluster.CLUSTER_ID);
            if (cluster != null) {
                isServer = false;
            } else {
                logger.error("{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
                return false;
            }
        }

        attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_OCCUPIEDHEATINGSETPOINT);
        if (attribute == null) {
            logger.error("{}: Error opening device thermostat occupied heating setpoint {} attribute",
                    endpoint.getIeeeAddress(), isServer ? "server" : "client");
            return false;
        }

        // Add a listener
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleCommand(final Command command) {
        if (!isServer) {
            logger.warn("{}: Thermostat occupied heating setpoint server can't be commanded",
                    endpoint.getIeeeAddress());
            return;
        }

        Integer value = temperatureToValue(command);

        if (value == null) {
            logger.warn("{}: Thermostat occupied heating setpoint {} [{}] was not processed", endpoint.getIeeeAddress(),
                    command, command.getClass().getSimpleName());
            return;
        }

        monitorCommandResponse(command, attribute.writeValue(value));
    }

    @Override
    public void handleRefresh() {
        if (!isServer) {
            return;
        }
        attribute.readValue(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclThermostatCluster cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster != null) {
            // Try to read the setpoint attribute
            ZclAttribute attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_OCCUPIEDHEATINGSETPOINT);
            Object value = attribute.readValue(Long.MAX_VALUE);
            if (value == null) {
                logger.trace("{}: Thermostat occupied heating setpoint returned null", endpoint.getIeeeAddress());
                return null;
            }
        } else {
            cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
            if (cluster == null) {
                return null;
            }

            // Should this check the supported commands to make sure this is really supported?
            // Downside is not all devices may report this!
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint,
                        ZigBeeBindingConstants.CHANNEL_NAME_THERMOSTAT_OCCUPIEDHEATING),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER_TEMPERATURE)
                .withType(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_OCCUPIEDHEATING)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_THERMOSTAT_OCCUPIEDHEATING)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_OCCUPIEDHEATINGSETPOINT) {
            updateChannelState(valueToTemperature((Integer) val));
        }
    }

}
