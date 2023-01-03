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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.config.ZclFanControlConfig;
import org.openhab.core.config.core.Configuration;
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
import com.zsmartsystems.zigbee.zcl.clusters.ZclFanControlCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * This channel supports fan control
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterFanControl extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterFanControl.class);

    private static final int MODE_OFF = 0;
    private static final int MODE_LOW = 1;
    private static final int MODE_MEDIUM = 2;
    private static final int MODE_HIGH = 3;
    private static final int MODE_ON = 4;
    private static final int MODE_AUTO = 5;

    private ZclFanControlCluster cluster;
    private ZclAttribute fanModeAttribute;

    private ZclFanControlConfig configFanControl;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclFanControlCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        ZclFanControlCluster serverCluster = (ZclFanControlCluster) endpoint
                .getInputCluster(ZclFanControlCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device fan controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclFanControlCluster.ATTR_FANMODE);
                CommandResult reportingResponse = attribute.setReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        cluster = (ZclFanControlCluster) endpoint.getInputCluster(ZclFanControlCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device fan controls", endpoint.getIeeeAddress());
            return false;
        }

        fanModeAttribute = cluster.getAttribute(ZclFanControlCluster.ATTR_FANMODE);

        // TODO: Detect the supported features and provide these as a description
        ZclAttribute fanSequenceAttribute = cluster.getAttribute(ZclFanControlCluster.ATTR_FANMODESEQUENCE);
        Integer sequence = (Integer) fanSequenceAttribute.readValue(Long.MAX_VALUE);
        if (sequence != null) {
            List<StateOption> options = new ArrayList<>();
            switch (sequence) {
                case 0:
                    options.add(new StateOption("1", "Low"));
                    options.add(new StateOption("2", "Medium"));
                    options.add(new StateOption("3", "High"));
                case 1:
                    options.add(new StateOption("1", "Low"));
                    options.add(new StateOption("3", "High"));
                    break;
                case 2:
                    options.add(new StateOption("1", "Low"));
                    options.add(new StateOption("2", "Medium"));
                    options.add(new StateOption("3", "High"));
                    options.add(new StateOption("5", "Auto"));
                    break;
                case 3:
                    options.add(new StateOption("1", "Low"));
                    options.add(new StateOption("3", "High"));
                    options.add(new StateOption("5", "Auto"));
                    break;
                case 4:
                    options.add(new StateOption("4", "On"));
                    options.add(new StateOption("5", "Auto"));
                    break;
                default:
                    logger.error("{}: Unknown fan mode sequence {}", endpoint.getIeeeAddress(), sequence);
                    break;
            }

            stateDescription = StateDescriptionFragmentBuilder.create().withMinimum(BigDecimal.ZERO)
                    .withMaximum(BigDecimal.valueOf(9)).withStep(BigDecimal.valueOf(1)).withPattern("")
                    .withReadOnly(false).withOptions(options).build().toStateDescription();

        }

        // Add the listener
        cluster.addAttributeListener(this);

        configFanControl = new ZclFanControlConfig();
        configFanControl.initialize(cluster);
        configOptions = new ArrayList<>();
        configOptions.addAll(configFanControl.getConfiguration());

        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device fan control cluster", endpoint.getIeeeAddress());

        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        fanModeAttribute.readValue(0);
    }

    @Override
    public void handleCommand(final Command command) {
        int value;
        if (command instanceof OnOffType) {
            value = command == OnOffType.ON ? MODE_ON : MODE_OFF;
        } else if (command instanceof DecimalType) {
            value = ((DecimalType) command).intValue();
        } else {
            logger.debug("{}: Unabled to convert fan mode {}", endpoint.getIeeeAddress(), command);
            return;
        }

        monitorCommandResponse(command, fanModeAttribute.writeValue(value));
    }

    @Override
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {

        configFanControl.updateConfiguration(currentConfiguration, updatedParameters);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclFanControlCluster cluster = (ZclFanControlCluster) endpoint.getInputCluster(ZclFanControlCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.trace("{}: Fan control cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_FANCONTROL),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_FANCONTROL)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_FANCONTROL).withProperties(createProperties(endpoint))
                .build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.FAN_CONTROL
                && attribute.getId() == ZclFanControlCluster.ATTR_FANMODE) {
            Integer value = (Integer) val;
            if (value != null) {
                updateChannelState(new DecimalType(value));
            }
        }
    }
}
