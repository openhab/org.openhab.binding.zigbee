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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.openhab.core.types.StateOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the thermostat system mode channel. The SystemMode attribute specifies the current operating mode of
 * the thermostat,
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterThermostatSystemMode extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterThermostatSystemMode.class);

    private final static int STATE_MIN = 0;
    private final static int STATE_MAX = 9;
    private final static int STATE_OFF = 0;
    private final static int STATE_AUTO = 1;

    private ZclThermostatCluster cluster;
    private ZclAttribute attribute;

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

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_SYSTEMMODE);
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

        attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_SYSTEMMODE);
        if (attribute == null) {
            logger.error("{}: Error opening device thermostat system mode attribute", endpoint.getIeeeAddress());
            return false;
        }

        // Sequence of operation defines the allowable modes
        ZclAttribute attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_CONTROLSEQUENCEOFOPERATION);
        Integer states = (Integer) attribute.readValue(Long.MAX_VALUE);
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

        stateDescription = StateDescriptionFragmentBuilder.create().withMinimum(BigDecimal.ZERO)
                .withMaximum(BigDecimal.valueOf(9)).withStep(BigDecimal.valueOf(1)).withPattern("").withReadOnly(false)
                .withOptions(options).build().toStateDescription();

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
        Integer value = null;
        if (command instanceof OnOffType) {
            // OnOff switches between OFF=OFF and ON=AUTO
            value = ((OnOffType) command) == OnOffType.ON ? STATE_AUTO : STATE_OFF;
        } else if (command instanceof Number) {
            value = ((Number) command).intValue();
        }

        if (value == null) {
            logger.warn("{}: System mode command {} [{}] was not processed", endpoint.getIeeeAddress(), command,
                    command.getClass().getSimpleName());
            return;
        }

        if (value < STATE_MIN || value > STATE_MAX) {
            logger.warn("{}: System mode command {} [{}], value {}, was out of limits", endpoint.getIeeeAddress(),
                    command, command.getClass().getSimpleName(), value);
            return;
        }

        monitorCommandResponse(command, attribute.writeValue(value));
    }

    @Override
    public void handleRefresh() {
        attribute.readValue(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclThermostatCluster cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.trace("{}: Thermostat cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        // Try to read the setpoint attribute
        ZclAttribute attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_SYSTEMMODE);
        Object value = attribute.readValue(Long.MAX_VALUE);
        if (value == null) {
            logger.trace("{}: Thermostat system mode returned null", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_THERMOSTAT_SYSTEMMODE),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_SYSTEMMODE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_THERMOSTAT_SYSTEMMODE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_SYSTEMMODE) {
            Integer value = (Integer) val;
            updateChannelState(new DecimalType(value));
        }
    }
}
