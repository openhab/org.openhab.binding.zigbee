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
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.ConfigDescriptionParameter.Type;
import org.openhab.core.config.core.ConfigDescriptionParameterBuilder;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.Command;
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
    private static final String CONFIG_INVERTCONTROL = CONFIG_ID + "invertcontrol";
    private static final String CONFIG_INVERTREPORT = CONFIG_ID + "invertreport";

    private ZclLevelControlCluster levelControlCluster;
    private int defaultTransitionTime = 10;
    private boolean invertControl = false;
    private boolean invertReport = false;

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

        options = new ArrayList<ParameterOption>();
        parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_INVERTCONTROL, Type.BOOLEAN)
                .withLabel("Invert Level Commands")
                .withDescription("Invert all level control commands sent to the device").withDefault("false").build());

        parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_INVERTCONTROL, Type.BOOLEAN)
                .withLabel("Invert Level Reports")
                .withDescription("Invert all level control reports received from the device").withDefault("false")
                .build());

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
    public boolean updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> configurationParameters) {

        boolean updated = false;
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
                case CONFIG_INVERTCONTROL:
                    invertControl = ((Boolean) (configurationParameter.getValue())).booleanValue();
                    break;
                case CONFIG_INVERTREPORT:
                    invertReport = ((Boolean) (configurationParameter.getValue())).booleanValue();
                    break;
                default:
                    logger.warn("{}: Unhandled configuration property {}", levelControlCluster.getZigBeeAddress(),
                            configurationParameter.getKey());
                    break;
            }

            if (response != null) {
                currentConfiguration.put(configurationParameter.getKey(), BigInteger.valueOf(response));
                updated = true;
            }
        }

        return updated;
    }

    /**
     * Gets the default transition time to be used when sending commands to the cluster
     *
     * @return the current defaultTransitionTime
     */
    public int getDefaultTransitionTime() {
        return defaultTransitionTime;
    }

    /**
     * Handles the inversion of the command as required
     *
     * @param command
     * @return
     */
    public Command handleInvertControl(Command command) {
        if (command instanceof PercentType) {
            if (invertControl) {
                return new PercentType(100 - ((PercentType) command).intValue());
            }
        }
        return command;
    }

    /**
     * Handles the inversion of the report as required
     *
     * @param lastLevel
     * @return
     */
    public PercentType handleInvertReport(PercentType Level) {
        if (invertReport) {
            return new PercentType(100 - Level.intValue());
        }
        return Level;
    }
}
