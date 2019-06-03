/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.internal.converter.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter.Type;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterBuilder;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.ParameterOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;

/**
 * Configuration handler for the {@link ZclLevelControlCluster}
 *
 * @author Chris Jackson
 *
 */
public class ZclLevelControlConfig implements ZclClusterConfigHandler {
    private Logger logger = LoggerFactory.getLogger(ZclLevelControlConfig.class);

    private static final String CONFIG_ID = "zigbee_levelcontrol_";
    private static final String CONFIG_DEFAULTTRANSITIONTIME = CONFIG_ID + "transitiontimedefault";
    private static final String CONFIG_ONOFFTRANSITIONTIME = CONFIG_ID + "transitiontimeonoff";
    private static final String CONFIG_ONTRANSITIONTIME = CONFIG_ID + "transitiontimeon";
    private static final String CONFIG_OFFTRANSITIONTIME = CONFIG_ID + "transitiontimeoff";
    private static final String CONFIG_ONLEVEL = CONFIG_ID + "onlevel";
    private static final String CONFIG_DEFAULTMOVERATE = CONFIG_ID + "defaultrate";

    private ZclLevelControlCluster levelControlCluster;
    private int defaultTransitionTime = 10;

    private final List<ConfigDescriptionParameter> parameters = new ArrayList<>();

    @Override
    public boolean initialize(ZclCluster cluster) {
        levelControlCluster = (ZclLevelControlCluster) cluster;
        try {
            Boolean result = levelControlCluster.discoverAttributes(false).get();
            if (!result) {
                logger.debug("{}: Unable to get supported attributes for {}.", levelControlCluster.getZigBeeAddress(),
                        levelControlCluster.getClusterName());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Error getting supported attributes for {}. ", levelControlCluster.getZigBeeAddress(),
                    levelControlCluster.getClusterName(), e);
        }

        // Build a list of configuration supported by this channel based on the attributes the cluster supports
        List<ParameterOption> options = new ArrayList<>();
        options.add(new ParameterOption("65535", "Use On/Off times"));
        parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_DEFAULTTRANSITIONTIME, Type.INTEGER)
                .withLabel("Default Transition Time")
                .withDescription("Default time in 100ms intervals to transition between ON and OFF").withDefault("0")
                .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                .withLimitToOptions(false).build());

        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONOFFTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONOFFTRANSITIONTIME, Type.INTEGER)
                    .withLabel("On/Off Transition Time")
                    .withDescription("Time in 100ms intervals to transition between ON and OFF").withDefault("0")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("65535", "Use On/Off transition time"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONTRANSITIONTIME, Type.INTEGER)
                    .withLabel("On Transition Time")
                    .withDescription("Time in 100ms intervals to transition from OFF to ON").withDefault("65535")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_OFFTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("65535", "Use On/Off transition time"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_OFFTRANSITIONTIME, Type.INTEGER)
                    .withLabel("Off Transition Time")
                    .withDescription("Time in 100ms intervals to transition from ON to OFF").withDefault("65535")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONLEVEL)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("255", "Not Set"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONLEVEL, Type.INTEGER).withLabel("On Level")
                    .withDescription("Default On level").withDefault("255").withMinimum(new BigDecimal(0))
                    .withMaximum(new BigDecimal(60000)).withOptions(options).withLimitToOptions(false).build());
        }
        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_DEFAULTMOVERATE)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("255", "Not Set"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_DEFAULTMOVERATE, Type.INTEGER)
                    .withLabel("Default move rate").withDescription("Move rate in steps per second").withDefault("255")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }

        return !parameters.isEmpty();
    }

    @Override
    public List<ConfigDescriptionParameter> getConfiguration() {
        return parameters;
    }

    @Override
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> configurationParameters) {

        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            if (!configurationParameter.getKey().startsWith(CONFIG_ID)) {
                continue;
            }
            // Ignore any configuration parameters that have not changed
            if (Objects.equals(configurationParameter.getValue(),
                    currentConfiguration.get(configurationParameter.getKey()))) {
                logger.debug("Configuration update: Ignored {} as no change", configurationParameter.getKey());
                continue;
            }

            logger.debug("{}: Update LevelControl configuration property {}->{} ({})",
                    levelControlCluster.getZigBeeAddress(), configurationParameter.getKey(),
                    configurationParameter.getValue(), configurationParameter.getValue().getClass().getSimpleName());
            Integer response = null;
            switch (configurationParameter.getKey()) {
                case CONFIG_ONOFFTRANSITIONTIME:
                    levelControlCluster
                            .setOnOffTransitionTime(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = levelControlCluster.getOnOffTransitionTime(0);
                    break;
                case CONFIG_ONTRANSITIONTIME:
                    levelControlCluster
                            .setOnTransitionTime(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = levelControlCluster.getOnTransitionTime(0);
                    break;
                case CONFIG_OFFTRANSITIONTIME:
                    levelControlCluster
                            .setOffTransitionTime((((BigDecimal) (configurationParameter.getValue())).intValue()));
                    response = levelControlCluster.getOffTransitionTime(0);
                    break;
                case CONFIG_ONLEVEL:
                    levelControlCluster.setOnLevel(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = levelControlCluster.getOnLevel(0);
                    break;
                case CONFIG_DEFAULTMOVERATE:
                    levelControlCluster
                            .setDefaultMoveRate(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = levelControlCluster.getDefaultMoveRate(0);
                    break;
                case CONFIG_DEFAULTTRANSITIONTIME:
                    defaultTransitionTime = ((BigDecimal) (configurationParameter.getValue())).intValue();
                    break;
                default:
                    logger.warn("{}: Unhandled configuration property {}", levelControlCluster.getZigBeeAddress(),
                            configurationParameter.getKey());
                    break;
            }

            if (response != null) {
                currentConfiguration.put(configurationParameter.getKey(), BigInteger.valueOf(response));
            }
        }
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
