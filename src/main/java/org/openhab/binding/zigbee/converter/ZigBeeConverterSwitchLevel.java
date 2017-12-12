/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter.Type;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterBuilder;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.ParameterOption;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final String TRANSITION_TIME = "zigbee_levelcontrol_transitiontime";
    private final String TRANSITION_TIME_ON_OFF = "zigbee_levelcontrol_transitiontimeonoff";
    private final String TRANSITION_TIME_ON = "zigbee_levelcontrol_transitiontimeon";
    private final String TRANSITION_TIME_OFF = "zigbee_levelcontrol_transitiontimeoff";
    private final String ON_LEVEL = "zigbee_levelcontrol_onlevel";
    private final String DEFAULT_MOVE_RATE = "zigbee_levelcontrol_defaultrate";

    @Override
    public boolean initializeConverter() {
        clusterLevelControl = (ZclLevelControlCluster) endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.debug("Error opening device level controls {}", endpoint.getIeeeAddress());
            return false;
        }

        clusterLevelControl.bind();

        // Add a listener, then request the status
        clusterLevelControl.addAttributeListener(this);
        clusterLevelControl.getCurrentLevel(0);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        clusterLevelControl.setCurrentLevelReporting(1, 600, 1);

        // try {
        // Set<Integer> supportedAttributes = null;
        // Boolean result = clusterLevelControl.discoverAttributes(false).get();
        // if (result) {
        // supportedAttributes = clusterLevelControl.getSupportedAttributes();
        // }

        // } catch (InterruptedException | ExecutionException e) {
        // logger.debug("Error getting supported attributes. ", e);
        // }
        List<ConfigDescriptionParameter> parameters = new ArrayList<ConfigDescriptionParameter>();

        List<ParameterOption> options = new ArrayList<ParameterOption>();
        options.add(new ParameterOption("65535", "Use On/Off transition time"));
        parameters.add(ConfigDescriptionParameterBuilder.create(TRANSITION_TIME, Type.INTEGER)
                .withLabel("Transition Time").withDescription("Time in 100ms intervals to transition between settings")
                .withDefault("0").withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                .withLimitToOptions(false).build());

        configOptions = parameters;

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
        int level = 0;
        if (command instanceof PercentType) {
            level = ((PercentType) command).intValue();
        } else if (command instanceof OnOffType) {
            if ((OnOffType) command == OnOffType.ON) {
                level = 100;
            } else {
                level = 0;
            }
        }

        clusterLevelControl.moveToLevelWithOnOffCommand((int) (level * 254.0 / 100.0 + 0.5), 10);
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
        Configuration updatedConfiguration = new Configuration();

        for (String property : configuration.getProperties().keySet()) {
            switch (property) {
                case TRANSITION_TIME:
                    BigDecimal value = (BigDecimal) configuration.get(property);
                    clusterLevelControl.setOnOffTransitionTime(value.intValue());
                    Integer response = clusterLevelControl.getOnOffTransitionTime(0);
                    if (response != null) {
                        updatedConfiguration.put(property, BigInteger.valueOf(response));
                    }
                    break;
                default:
                    logger.debug("{}: Unhandled configuration property {}", endpoint.getIeeeAddress(), property);
                    break;
            }
        }

        return updatedConfiguration;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {} from {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.LEVEL_CONTROL
                && attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                updateChannelState(new PercentType(value * 100 / 254));
            }
        }
    }
}
