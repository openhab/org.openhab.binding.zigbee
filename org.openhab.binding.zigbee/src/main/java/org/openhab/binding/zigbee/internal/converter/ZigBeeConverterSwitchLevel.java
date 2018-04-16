/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
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
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterSwitchLevel extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchLevel.class);

    private ZclLevelControlCluster clusterLevelControl;
    private ZclLevelControlConfig configLevelControl;

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
                // Configure reporting - no faster than once per second - no slower than 10 minutes.
                CommandResult reportingResponse = clusterLevelControl
                        .setCurrentLevelReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                if (reportingResponse.isError()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }

        // Add a listener, then request the status
        clusterLevelControl.addAttributeListener(this);

        // Create a configuration handler and get the available options
        configLevelControl = new ZclLevelControlConfig(clusterLevelControl);
        configOptions = configLevelControl.getConfiguration();

        return true;
    }

    @Override
    public void disposeConverter() {
        clusterLevelControl.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
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
            return null;
        }
        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL,
                ZigBeeBindingConstants.ITEM_TYPE_DIMMER, "Dimmer");
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
            Integer level = (Integer) attribute.getLastValue();
            if (level != null) {
                updateChannelState(levelToPercent(level));
            }
            return;
        }
    }
}
