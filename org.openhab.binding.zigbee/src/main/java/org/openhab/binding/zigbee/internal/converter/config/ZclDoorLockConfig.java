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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclDoorLockCluster;

/**
 * Configuration handler for the {@link ZclDoorLockCluster}
 *
 * @author Chris Jackson
 *
 */
public class ZclDoorLockConfig implements ZclClusterConfigHandler {
    private Logger logger = LoggerFactory.getLogger(ZclDoorLockConfig.class);

    private static final String CONFIG_ID = "zigbee_doorlock_";
    private static final String CONFIG_SOUNDVOLUME = CONFIG_ID + "soundvolume";
    private static final String CONFIG_ENABLEONETOUCHLOCKING = CONFIG_ID + "onetouchlocking";
    private static final String CONFIG_ENABLELOCALPROGRAMMING = CONFIG_ID + "localprogramming";
    private static final String CONFIG_AUTORELOCKTIME = CONFIG_ID + "autorelocktime";

    private final List<ConfigDescriptionParameter> parameters = new ArrayList<>();

    private ZclDoorLockCluster doorLockCluster;

    @Override
    public boolean initialize(ZclCluster cluster) {
        doorLockCluster = (ZclDoorLockCluster) cluster;
        try {
            Boolean result = doorLockCluster.discoverAttributes(false).get();
            if (!result) {
                logger.debug("{}: Unable to get supported attributes for {}.", doorLockCluster.getZigBeeAddress(),
                        doorLockCluster.getClusterName());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Error getting supported attributes for {}. ", doorLockCluster.getZigBeeAddress(),
                    doorLockCluster.getClusterName(), e);
        }

        // Build a list of configuration supported by this channel based on the attributes the cluster supports
        List<ParameterOption> options = new ArrayList<>();

        if (doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_SOUNDVOLUME)) {
            options = new ArrayList<>();
            options.add(new ParameterOption("0", "Silent"));
            options.add(new ParameterOption("1", "Low"));
            options.add(new ParameterOption("2", "High"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_SOUNDVOLUME, Type.INTEGER)
                    .withLabel("The sound volume of the door lock").withDescription("").withDefault("0")
                    .withOptions(options).build());
        }
        if (doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_AUTORELOCKTIME)) {
            options = new ArrayList<>();
            options.add(new ParameterOption("0", "Disabled"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_AUTORELOCKTIME, Type.INTEGER)
                    .withLabel("Enable one touch locking").withDescription("").withMinimum(BigDecimal.ZERO)
                    .withMaximum(new BigDecimal(3600)).withDefault("0").withOptions(options).build());
        }
        if (doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_ENABLEONETOUCHLOCKING)) {
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ENABLEONETOUCHLOCKING, Type.BOOLEAN)
                    .withLabel("Set auto relock time").withDescription("").withDefault("false").build());
        }
        if (doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_ENABLELOCALPROGRAMMING)) {
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ENABLELOCALPROGRAMMING, Type.BOOLEAN)
                    .withLabel("Enable local programming").withDescription("").withDefault("false").build());
        }

        return !parameters.isEmpty();
    }

    @Override
    public List<ConfigDescriptionParameter> getConfiguration() {
        return parameters;
    }

    @Override
    public boolean updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        boolean updated = false;
        for (Entry<String, Object> configurationParameter : updatedParameters.entrySet()) {
            if (!configurationParameter.getKey().startsWith(CONFIG_ID)) {
                continue;
            }

            // Ignore any configuration parameters that have not changed
            if (Objects.equals(configurationParameter.getValue(),
                    currentConfiguration.get(configurationParameter.getKey()))) {
                logger.debug("Configuration update: Ignored {} as no change", configurationParameter.getKey());
                continue;
            }

            logger.debug("{}: Update DoorLock configuration property {}->{} ({})", doorLockCluster.getZigBeeAddress(),
                    configurationParameter.getKey(), configurationParameter.getValue(),
                    configurationParameter.getValue().getClass().getSimpleName());
            Object response = null;
            switch (configurationParameter.getKey()) {
                case CONFIG_ENABLEONETOUCHLOCKING:
                    doorLockCluster
                            .setEnableOneTouchLocking(((Boolean) (configurationParameter.getValue())).booleanValue());
                    response = Boolean.valueOf(doorLockCluster.getEnableOneTouchLocking(0));
                    break;
                case CONFIG_ENABLELOCALPROGRAMMING:
                    doorLockCluster
                            .setEnableLocalProgramming(((Boolean) configurationParameter.getValue()).booleanValue());
                    response = Boolean.valueOf(doorLockCluster.getEnableLocalProgramming(0));
                    break;
                case CONFIG_SOUNDVOLUME:
                    doorLockCluster.setSoundVolume(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = BigInteger.valueOf(doorLockCluster.getSoundVolume(0));
                    break;
                case CONFIG_AUTORELOCKTIME:
                    doorLockCluster.setAutoRelockTime(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = BigInteger.valueOf(doorLockCluster.getAutoRelockTime(0));
                    break;
                default:
                    logger.warn("{}: Unhandled configuration property {}", doorLockCluster.getZigBeeAddress(),
                            configurationParameter.getKey());
                    break;
            }

            if (response != null) {
                currentConfiguration.put(configurationParameter.getKey(), response);
                updated = true;
            }
        }

        return updated;
    }
}
