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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.StateDescription;
import org.eclipse.smarthome.core.types.StateOption;
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
 * Converter for the thermostat running mode channel
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterThermostatRunningMode extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterThermostatRunningMode.class);

    private ZclThermostatCluster cluster;

    @Override
    public boolean initializeConverter() {
        cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
            return false;
        }

        CommandResult bindResponse;
        try {
            bindResponse = bind(cluster).get();
            if (!bindResponse.isSuccess()) {
                logger.debug("{}: Failed to bind thermostat cluster", endpoint.getIeeeAddress());
            } else {
                // Configure reporting
                ZclAttribute attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE);
                CommandResult reportingResponse = cluster
                        .setReporting(attribute, REPORTING_PERIOD_DEFAULT_MIN, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
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
        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        cluster.read(cluster.getAttribute(ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE));
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
                    logger.trace("{}: Thermostat running mode returned null", endpoint.getIeeeAddress());
                    return null;
                }
            } else if (!cluster.isAttributeSupported(ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE)) {
                logger.trace("{}: Thermostat running mode not supported", endpoint.getIeeeAddress());
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in thermostat cluster", endpoint.getIeeeAddress(), e);
        }

        // Sequence of operation defines the allowable modes
        Integer states = cluster.getControlSequenceOfOperation(Long.MAX_VALUE);

        // Define the allowable states
        List<StateOption> options = new ArrayList<>();
        options.add(new StateOption("0", "Off"));
        options.add(new StateOption("1", "Auto"));
        if (states != null && states != 0 && states != 1) {
            options.add(new StateOption("4", "Heat"));
            options.add(new StateOption("5", "Emergency Heating"));
        }
        if (states != null && states != 3 && states != 6) {
            options.add(new StateOption("3", "Cool"));
            options.add(new StateOption("6", "Precooling"));
        }
        options.add(new StateOption("7", "Fan Only"));
        options.add(new StateOption("8", "Dry"));
        options.add(new StateOption("9", "Sleep"));

        StateDescription description = new StateDescription(BigDecimal.ZERO, BigDecimal.valueOf(9),
                BigDecimal.valueOf(1), "", true, options);

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint,
                        ZigBeeBindingConstants.CHANNEL_NAME_THERMOSTAT_RUNNINGMODE),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_RUNNINGMODE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_THERMOSTAT_RUNNINGMODE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                updateChannelState(new DecimalType(value));
            }
        }
    }
}
