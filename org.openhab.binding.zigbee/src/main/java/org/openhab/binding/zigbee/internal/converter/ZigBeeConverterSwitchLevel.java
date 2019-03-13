/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.util.ArrayList;
import java.util.Map;
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
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.internal.converter.config.ZclLevelControlConfig;
import org.openhab.binding.zigbee.internal.converter.config.ZclReportingConfig;
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
 */
public class ZigBeeConverterSwitchLevel extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchLevel.class);

    private ZclOnOffCluster clusterOnOff;
    private ZclLevelControlCluster clusterLevelControl;

    private ZclReportingConfig configReporting;
    private ZclLevelControlConfig configLevelControl;

    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);

    private PercentType lastLevel = PercentType.HUNDRED;

    @Override
    public synchronized boolean initializeConverter() {
        clusterLevelControl = (ZclLevelControlCluster) endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.error("{}: Error opening device level controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(clusterLevelControl).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                CommandResult reportingResponse = clusterLevelControl
                        .setCurrentLevelReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
                logger.debug("{}: Failed to bind level control cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error(String.format("%s: Exception setting level control reporting ", endpoint.getIeeeAddress()), e);
        }

        clusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff != null) {
            try {
                CommandResult bindResponse = bind(clusterOnOff).get();
                if (bindResponse.isSuccess()) {
                    // Configure reporting
                    CommandResult reportingResponse = clusterOnOff.setOnOffReporting(1, REPORTING_PERIOD_DEFAULT_MAX)
                            .get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
                } else {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error(String.format("%s: Exception setting on off reporting ", endpoint.getIeeeAddress()), e);
            }

            // Set the currentOnOffState to ON. This will ensure that we only ignore levelControl reports AFTER we have
            // really received an OFF report, thus confirming ON_OFF reporting is working
            currentOnOffState.set(true);

            // Add a listener
            clusterOnOff.addAttributeListener(this);
        }

        // Add a listener
        clusterLevelControl.addAttributeListener(this);

        // Create a configuration handler and get the available options
        configReporting = new ZclReportingConfig();
        configLevelControl = new ZclLevelControlConfig();
        configLevelControl.initialize(clusterLevelControl);

        configOptions = new ArrayList<>();
        configOptions.addAll(configReporting.getConfiguration());
        configOptions.addAll(configLevelControl.getConfiguration());

        return true;
    }

    @Override
    public void disposeConverter() {
        clusterLevelControl.removeAttributeListener(this);
        if (clusterOnOff != null) {
            clusterOnOff.removeAttributeListener(this);
        }
    }

    @Override
    public int getPollingPeriod() {
        return configReporting.getPollingPeriod();
    }

    @Override
    public void handleRefresh() {
        if (clusterOnOff != null) {
            clusterOnOff.getOnOff(0);
        }
        clusterLevelControl.getCurrentLevel(0);
    }

    @Override
    public void handleCommand(final Command command) {
        if (command instanceof OnOffType) {
            handleOnOffCommand((OnOffType) command);
        } else if (command instanceof PercentType) {
            handlePercentCommand((PercentType) command);
        } else {
            logger.warn("{}: Level converter only accepts PercentType and OnOffType - not {}",
                    endpoint.getIeeeAddress(), command.getClass().getSimpleName());
        }
    }

    /**
     * If we support the OnOff cluster then we should perform the same function as the SwitchOnoffConverter. Otherwise,
     * interpret ON commands as moving to level 100%, and OFF commands as moving to level 0%.
     */
    private void handleOnOffCommand(OnOffType cmdOnOff) {
        if (clusterOnOff != null) {
            if (cmdOnOff == OnOffType.ON) {
                clusterOnOff.onCommand();
            } else {
                clusterOnOff.offCommand();
            }
        } else {
            if (cmdOnOff == OnOffType.ON) {
                moveToLevel(PercentType.HUNDRED);
            } else {
                moveToLevel(PercentType.ZERO);
            }
        }
    }

    private void handlePercentCommand(PercentType cmdPercent) {
        moveToLevel(cmdPercent);
    }

    private void moveToLevel(PercentType percent) {
        if (clusterOnOff != null) {
            if (percent.equals(PercentType.ZERO)) {
                clusterOnOff.offCommand();
            } else {
                clusterLevelControl.moveToLevelWithOnOffCommand(percentToLevel(percent),
                        configLevelControl.getDefaultTransitionTime());
            }
        } else {
            clusterLevelControl.moveToLevelCommand(percentToLevel(percent),
                    configLevelControl.getDefaultTransitionTime());
        }
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
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        configReporting.updateConfiguration(currentConfiguration, updatedParameters);
        configLevelControl.updateConfiguration(currentConfiguration, updatedParameters);
    }

    @Override
    public synchronized void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.LEVEL_CONTROL
                && attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
            lastLevel = levelToPercent((Integer) attribute.getLastValue());
            if (currentOnOffState.get()) {
                // Note that state is only updated if the current On/Off state is TRUE (ie ON)
                updateChannelState(lastLevel);
            }
        } else if (attribute.getCluster() == ZclClusterType.ON_OFF && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            if (attribute.getLastValue() != null) {
                currentOnOffState.set((Boolean) attribute.getLastValue());
                updateChannelState(currentOnOffState.get() ? lastLevel : OnOffType.OFF);
            }
        }
    }
}
