/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter.Type;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterBuilder;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.ParameterOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;

/**
 * Configuration handler for the {@link ZclLevelControlCluster}
 *
 * @author Chris Jackson
 *
 */
public class ZclLevelControlConfig implements ZclClusterConfigHandler {
    private Logger logger = LoggerFactory.getLogger(ZclLevelControlConfig.class);

    private final String CONFIG_DEFAULTTRANSITIONTIME = "zigbee_levelcontrol_transitiontimedefault";
    private final String CONFIG_ONOFFTRANSITIONTIME = "zigbee_levelcontrol_transitiontimeonoff";
    private final String CONFIG_ONTRANSITIONTIME = "zigbee_levelcontrol_transitiontimeon";
    private final String CONFIG_OFFTRANSITIONTIME = "zigbee_levelcontrol_transitiontimeoff";
    private final String CONFIG_ONLEVEL = "zigbee_levelcontrol_onlevel";
    private final String CONFIG_DEFAULTMOVERATE = "zigbee_levelcontrol_defaultrate";

    private final ZclLevelControlCluster cluster;
    private int defaultTransitionTime = 10;

    public ZclLevelControlConfig(ZclLevelControlCluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public List<ConfigDescriptionParameter> getConfiguration() {
        try {
            Boolean result = cluster.discoverAttributes(false).get();
            if (!result) {
                logger.debug("{}: Unable to get supported attributes for {}.", cluster.getZigBeeAddress(),
                        cluster.getClusterName());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Error getting supported attributes for {}. ", cluster.getZigBeeAddress(),
                    cluster.getClusterName(), e);
        }

        // Build a list of configuration supported by this channel based on the attributes the cluster supports
        List<ConfigDescriptionParameter> parameters = new ArrayList<ConfigDescriptionParameter>();

        List<ParameterOption> options = new ArrayList<ParameterOption>();
        parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_DEFAULTTRANSITIONTIME, Type.INTEGER)
                .withLabel("Default Transition Time")
                .withDescription("Default time in 10ms intervals to transition between ON and OFF").withDefault("0")
                .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                .withLimitToOptions(false).build());

        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONOFFTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONOFFTRANSITIONTIME, Type.INTEGER)
                    .withLabel("On/Off Transition Time")
                    .withDescription("Time in 10ms intervals to transition between ON and OFF").withDefault("0")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("65535", "On transition time"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONTRANSITIONTIME, Type.INTEGER)
                    .withLabel("On Transition Time")
                    .withDescription("Time in 10ms intervals to transition from OFF to ON").withDefault("65535")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_OFFTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("65535", "Off transition time"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_OFFTRANSITIONTIME, Type.INTEGER)
                    .withLabel("Off Transition Time")
                    .withDescription("Time in 10ms intervals to transition from ON to OFF").withDefault("65535")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONLEVEL)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("255", "Not Set"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONLEVEL, Type.INTEGER).withLabel("On Level")
                    .withDescription("Default On level").withDefault("255").withMinimum(new BigDecimal(0))
                    .withMaximum(new BigDecimal(60000)).withOptions(options).withLimitToOptions(false).build());
        }
        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_DEFAULTMOVERATE)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("255", "Not Set"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_DEFAULTMOVERATE, Type.INTEGER)
                    .withLabel("Default move rate").withDescription("Move rate in steps per second").withDefault("255")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }

        return parameters;
    }

    @Override
    public Configuration updateConfiguration(@NonNull Configuration configuration) {
        Configuration updatedConfiguration = new Configuration();

        for (String property : configuration.getProperties().keySet()) {
            logger.debug("{}: Update LevelControl configuration property {}->{} ({})", cluster.getZigBeeAddress(),
                    property, configuration.get(property), configuration.get(property).getClass().getSimpleName());
            Integer response = null;
            BigDecimal valueDecimal;
            switch (property) {
                case CONFIG_ONOFFTRANSITIONTIME:
                    valueDecimal = (BigDecimal) configuration.get(property);
                    cluster.setOnOffTransitionTime(valueDecimal.intValue());
                    response = cluster.getOnOffTransitionTime(0);
                    break;
                case CONFIG_ONTRANSITIONTIME:
                    valueDecimal = (BigDecimal) configuration.get(property);
                    cluster.setOnTransitionTime(valueDecimal.intValue());
                    response = cluster.getOnTransitionTime(0);
                    break;
                case CONFIG_OFFTRANSITIONTIME:
                    valueDecimal = (BigDecimal) configuration.get(property);
                    cluster.setOffTransitionTime(valueDecimal.intValue());
                    response = cluster.getOffTransitionTime(0);
                    break;
                case CONFIG_ONLEVEL:
                    valueDecimal = (BigDecimal) configuration.get(property);
                    cluster.setOnLevel(valueDecimal.intValue());
                    response = cluster.getOnLevel(0);
                    break;
                case CONFIG_DEFAULTMOVERATE:
                    valueDecimal = (BigDecimal) configuration.get(property);
                    cluster.setDefaultMoveRate(valueDecimal.intValue());
                    response = cluster.getDefaultMoveRate(0);
                    break;
                case CONFIG_DEFAULTTRANSITIONTIME:
                    defaultTransitionTime = ((BigDecimal) configuration.get(property)).intValue();
                    break;
                default:
                    logger.warn("{}: Unhandled configuration property {}", cluster.getZigBeeAddress(), property);
                    break;
            }

            if (response != null) {
                updatedConfiguration.put(property, BigInteger.valueOf(response));
            }
        }

        return updatedConfiguration;
    }

    /**
     * Gets the default transition time to be used when sending commands to the cluster
     *
     * @return the current defaultTransitionTime
     */
    public int getDefaultTransitionTime() {
        return defaultTransitionTime;
    }
}
