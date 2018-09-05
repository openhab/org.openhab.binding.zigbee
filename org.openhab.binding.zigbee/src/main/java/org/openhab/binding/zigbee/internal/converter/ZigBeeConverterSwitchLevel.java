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
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.internal.converter.config.ZclLevelControlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Level control converter uses both the {@link ZclLevelControlCluster} and the {@link ZclOnOffCluster}. If the
 * {@link ZclOnOffCluster} has reported the device is OFF, then reports from {@link ZclLevelControlCluster} are ignored.
 * This is required as devices can report via the {@link ZclLevelControlCluster} that they have a specified level, but
 * still be OFF.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterSwitchLevel extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchLevel.class);

    private ZclOnOffCluster clusterOnOffServer;
    private ZclLevelControlCluster clusterLevelControl;
    private ZclLevelControlConfig configLevelControl;

    private final AtomicBoolean currentState = new AtomicBoolean(true);

    private PercentType lastLevel = PercentType.HUNDRED;

    @Override
    public boolean initializeConverter() {
        clusterLevelControl = (ZclLevelControlCluster) endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.error("{}: Error opening device level controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = clusterLevelControl.bind().get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                CommandResult reportingResponse = clusterLevelControl
                        .setCurrentLevelReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                if (reportingResponse.isError()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
                logger.debug("{}: Failed to bind level control cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting level control reporting ", endpoint.getIeeeAddress(), e);
        }

        clusterOnOffServer = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffServer != null) {
            try {
                CommandResult bindResponse = clusterOnOffServer.bind().get();
                if (bindResponse.isSuccess()) {
                    // Configure reporting
                    CommandResult reportingResponse = clusterOnOffServer
                            .setOnOffReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                    if (reportingResponse.isError()) {
                        pollingPeriod = POLLING_PERIOD_HIGH;
                    }
                } else {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("{}: Exception setting on off reporting ", endpoint.getIeeeAddress(), e);
            }

            // Set the currentState to ON. This will ensure that we only ignore levelControl reports AFTER we have
            // really received an OFF report, thus confirming ON_OFF reporting is working
            currentState.set(true);

            // Add a listener
            clusterOnOffServer.addAttributeListener(this);
        }

        // Add a listener
        clusterLevelControl.addAttributeListener(this);

        // Create a configuration handler and get the available options
        configLevelControl = new ZclLevelControlConfig(clusterLevelControl);
        configOptions = configLevelControl.getConfiguration();

        return true;
    }

    @Override
    public void disposeConverter() {
        clusterLevelControl.removeAttributeListener(this);
        if (clusterOnOffServer != null) {
            clusterOnOffServer.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        if (clusterOnOffServer != null) {
            clusterOnOffServer.getOnOff(0);
        }
        clusterLevelControl.getCurrentLevel(0);
    }

    @Override
    public void handleCommand(final Command command) {
        PercentType percent;
        if (command instanceof PercentType) {
            percent = (PercentType) command;
        } else if (command instanceof OnOffType) {
            OnOffType cmdOnOff = (OnOffType) command;
            if (cmdOnOff == OnOffType.ON) {
                percent = PercentType.HUNDRED;
            } else {
                percent = PercentType.ZERO;
            }
        } else {
            logger.warn("{}: Level converter only accepts PercentType and OnOffType - not {}",
                    endpoint.getIeeeAddress(), command.getClass().getSimpleName());
            return;
        }

        clusterLevelControl.moveToLevelWithOnOffCommand(percentToLevel(percent),
                configLevelControl.getDefaultTransitionTime());
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Level control cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_SWITCH_LEVEL),
                        ZigBeeBindingConstants.ITEM_TYPE_DIMMER)
                .withType(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_SWITCH_LEVEL).withProperties(createProperties(endpoint))
                .build();
    }

    @Override
    public Configuration updateConfiguration(@NonNull Configuration configuration) {
        return configLevelControl.updateConfiguration(configuration);
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.LEVEL_CONTROL
                && attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
            lastLevel = levelToPercent((Integer) attribute.getLastValue());
            if (currentState.get()) {
                // Note that state is only updated if the current On/Off state is TRUE (ie ON)
                updateChannelState(lastLevel);
            }
            return;
        }
        if (attribute.getCluster() == ZclClusterType.ON_OFF && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            if (attribute.getLastValue() == null) {
                return;
            }
            currentState.set((Boolean) attribute.getLastValue());
            updateChannelState(currentState.get() ? lastLevel : OnOffType.OFF);
        }
    }
}
