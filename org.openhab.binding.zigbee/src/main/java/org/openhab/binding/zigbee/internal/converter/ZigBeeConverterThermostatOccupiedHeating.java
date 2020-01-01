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

import java.util.concurrent.ExecutionException;

import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
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
                ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_OCCUPIEDHEATINGSETPOINT);
                CommandResult reportingResponse = attribute
                        .setReporting(REPORTING_PERIOD_DEFAULT_MIN, REPORTING_PERIOD_DEFAULT_MAX, 0.1).get();
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

        attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_OCCUPIEDHEATINGSETPOINT);
        if (attribute == null) {
            logger.error("{}: Error opening device thermostat occupied heating setpoint attribute",
                    endpoint.getIeeeAddress());
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
    public void handleCommand(final Command command) {
        Integer value = temperatureToValue(command);

        if (value == null) {
            logger.warn("{}: Thermostat occupied heating setpoint {} [{}] was not processed", endpoint.getIeeeAddress(),
                    command, command.getClass().getSimpleName());
            return;
        }

        attribute.writeValue(value);
    }

    @Override
    public void handleRefresh() {
        cluster.getOccupiedHeatingSetpoint(0);
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
                Integer capabilities = cluster.getOccupiedHeatingSetpoint(Long.MAX_VALUE);
                if (capabilities == null) {
                    logger.trace("{}: Thermostat occupied heating setpoint returned null", endpoint.getIeeeAddress());
                    return null;
                }
            } else if (!cluster.isAttributeSupported(ZclThermostatCluster.ATTR_OCCUPIEDHEATINGSETPOINT)) {
                logger.trace("{}: Thermostat occupied heating setpoint not supported", endpoint.getIeeeAddress());
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in thermostat cluster", endpoint.getIeeeAddress(), e);
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
        if (attribute.getCluster() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_OCCUPIEDHEATINGSETPOINT) {
            updateChannelState(valueToTemperature((Integer) val));
        }
    }
}
