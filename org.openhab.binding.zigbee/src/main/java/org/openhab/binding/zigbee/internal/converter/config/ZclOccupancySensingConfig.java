/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOccupancySensingCluster;

/**
 * Configuration handler for the {@link ZclLevelControlCluster}
 *
 * @author Chris Jackson
 *
 */
public class ZclOccupancySensingConfig implements ZclClusterConfigHandler {
    private Logger logger = LoggerFactory.getLogger(ZclOccupancySensingConfig.class);

    private static final String CONFIG_ID = "zigbee_occupancysensing_";
    private static final String CONFIG_ULTRASONICUNOCCUPIEDTOOCCUPIEDDELAY = CONFIG_ID + "unooccupiedtooccupieddelay";
    private static final String CONFIG_ULTRASONICOCCUPIEDTOUNOCCUPIEDDELAY = CONFIG_ID + "occupiedtounoccupieddelay";
    private static final String CONFIG_ULTRASONICUNOCCUPIEDTOOCCUPIEDTHRESHOLD = CONFIG_ID
            + "occupiedtounoccupiedthreshold";

    private ZclOccupancySensingCluster occupancySensingCluster;

    private final List<ConfigDescriptionParameter> parameters = new ArrayList<>();

    @Override
    public boolean initialize(ZclCluster cluster) {
        occupancySensingCluster = (ZclOccupancySensingCluster) cluster;
        try {
            Boolean result = occupancySensingCluster.discoverAttributes(false).get();
            if (!result) {
                logger.debug("{}: Unable to get supported attributes for {}.",
                        occupancySensingCluster.getZigBeeAddress(), occupancySensingCluster.getClusterName());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Error getting supported attributes for {}. ", occupancySensingCluster.getZigBeeAddress(),
                    occupancySensingCluster.getClusterName(), e);
        }

        // Build a list of configuration supported by this channel based on the attributes the cluster supports

        if (occupancySensingCluster
                .isAttributeSupported(ZclOccupancySensingCluster.ATTR_ULTRASONICUNOCCUPIEDTOOCCUPIEDDELAY)) {
            parameters.add(
                    ConfigDescriptionParameterBuilder.create(CONFIG_ULTRASONICUNOCCUPIEDTOOCCUPIEDDELAY, Type.INTEGER)
                            .withLabel("Unoccupied to occupied delay")
                            .withDescription("Time in seconds before switching to occupied").withDefault("0")
                            .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).build());
        }
        if (occupancySensingCluster
                .isAttributeSupported(ZclOccupancySensingCluster.ATTR_ULTRASONICOCCUPIEDTOUNOCCUPIEDDELAY)) {
            parameters.add(
                    ConfigDescriptionParameterBuilder.create(CONFIG_ULTRASONICOCCUPIEDTOUNOCCUPIEDDELAY, Type.INTEGER)
                            .withLabel("Occupied to unoccupied delay")
                            .withDescription("Time in seconds before switching to unoccupied").withDefault("0")
                            .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).build());
        }
        if (occupancySensingCluster
                .isAttributeSupported(ZclOccupancySensingCluster.ATTR_ULTRASONICUNOCCUPIEDTOOCCUPIEDTHRESHOLD)) {
            parameters.add(ConfigDescriptionParameterBuilder
                    .create(CONFIG_ULTRASONICUNOCCUPIEDTOOCCUPIEDTHRESHOLD, Type.INTEGER)
                    .withLabel("Unoccupied to occupied threshold")
                    .withDescription(
                            "Event threshold that must be met in the period Unoccupied to Occupied delay period before the Ultrasonic sensor changes to its occupied state")
                    .withDefault("0").withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(255)).build());
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

            logger.debug("{}: Update OccupancySensing configuration property {}->{} ({})",
                    occupancySensingCluster.getZigBeeAddress(), configurationParameter.getKey(),
                    configurationParameter.getValue(), configurationParameter.getValue().getClass().getSimpleName());
            Integer response = null;
            switch (configurationParameter.getKey()) {
                case CONFIG_ULTRASONICUNOCCUPIEDTOOCCUPIEDDELAY:
                    response = configureAttribute(ZclOccupancySensingCluster.ATTR_ULTRASONICUNOCCUPIEDTOOCCUPIEDDELAY,
                            ((BigDecimal) (configurationParameter.getValue())).intValue());
                    break;
                case CONFIG_ULTRASONICOCCUPIEDTOUNOCCUPIEDDELAY:
                    response = configureAttribute(ZclOccupancySensingCluster.ATTR_ULTRASONICOCCUPIEDTOUNOCCUPIEDDELAY,
                            ((BigDecimal) (configurationParameter.getValue())).intValue());
                    break;
                case CONFIG_ULTRASONICUNOCCUPIEDTOOCCUPIEDTHRESHOLD:
                    response = configureAttribute(
                            ZclOccupancySensingCluster.ATTR_ULTRASONICUNOCCUPIEDTOOCCUPIEDTHRESHOLD,
                            ((BigDecimal) (configurationParameter.getValue())).intValue());
                    break;
                default:
                    logger.warn("{}: Unhandled configuration property {}", occupancySensingCluster.getZigBeeAddress(),
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

    private Integer configureAttribute(int attributeId, Object value) {
        ZclAttribute attribute = occupancySensingCluster.getAttribute(attributeId);
        attribute.writeValue(((Integer) (value)).intValue());
        return (Integer) attribute.readValue(0);
    }

    @Override
    public void updateCurrentConfiguration(Configuration currentConfig) {
        currentConfig.put(CONFIG_ULTRASONICUNOCCUPIEDTOOCCUPIEDDELAY,
                occupancySensingCluster
                        .getAttribute(ZclOccupancySensingCluster.ATTR_ULTRASONICUNOCCUPIEDTOOCCUPIEDDELAY)
                        .readValue(Long.MAX_VALUE));
        currentConfig.put(CONFIG_ULTRASONICOCCUPIEDTOUNOCCUPIEDDELAY,
                occupancySensingCluster
                        .getAttribute(ZclOccupancySensingCluster.ATTR_ULTRASONICOCCUPIEDTOUNOCCUPIEDDELAY)
                        .readValue(Long.MAX_VALUE));
        currentConfig.put(CONFIG_ULTRASONICUNOCCUPIEDTOOCCUPIEDTHRESHOLD,
                occupancySensingCluster
                        .getAttribute(ZclOccupancySensingCluster.ATTR_ULTRASONICUNOCCUPIEDTOOCCUPIEDTHRESHOLD)
                        .readValue(Long.MAX_VALUE));
    }

}
