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

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;

/**
 * Configuration handler for the {@link ZclOnOffSwitchCluster}
 *
 * @author Chris Jackson
 *
 */
public class ZclOnOffSwitchConfig implements ZclClusterConfigHandler {
    private Logger logger = LoggerFactory.getLogger(ZclOnOffSwitchConfig.class);

    private static final String CONFIG_ID = "zigbee_onoff_";
    private static final String CONFIG_OFFWAITTIME = CONFIG_ID + "offwaittime";
    private static final String CONFIG_ONTIME = CONFIG_ID + "ontime";
    private static final String CONFIG_STARTUPONOFF = CONFIG_ID + "startuponoff";

    private ZclOnOffCluster onoffCluster;

    private final List<ConfigDescriptionParameter> parameters = new ArrayList<>();

    @Override
    public boolean initialize(ZclCluster cluster) {
        onoffCluster = (ZclOnOffCluster) cluster;
        try {
            Boolean result = onoffCluster.discoverAttributes(false).get();
            if (!result) {
                logger.debug("{}: Unable to get supported attributes for {}.", onoffCluster.getZigBeeAddress(),
                        onoffCluster.getClusterName());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Error getting supported attributes for {}. ", onoffCluster.getZigBeeAddress(),
                    onoffCluster.getClusterName(), e);
        }

        // Build a list of configuration supported by this channel based on the attributes the cluster supports
        List<ParameterOption> options = new ArrayList<>();

        if (onoffCluster.isAttributeSupported(ZclOnOffCluster.ATTR_OFFWAITTIME)) {
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_OFFWAITTIME, Type.INTEGER)
                    .withLabel("Off Wait Time")
                    .withDescription("Time in 100ms steps to ignore ON commands after an OFF command").withDefault("0")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).build());
        }
        if (onoffCluster.isAttributeSupported(ZclOnOffCluster.ATTR_ONTIME)) {
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONTIME, Type.INTEGER)
                    .withLabel("Auto OFF Time")
                    .withDescription("Time in 100ms steps to automatically turn off when sent with timed command")
                    .withDefault("65535").withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).build());
        }
        if (onoffCluster.isAttributeSupported(ZclOnOffCluster.ATTR_STARTUPONOFF)) {
            options = new ArrayList<>();
            options.add(new ParameterOption("0", "OFF"));
            options.add(new ParameterOption("1", "ON"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_STARTUPONOFF, Type.INTEGER)
                    .withLabel("Power on state").withDescription("The state to set after powering on").withDefault("0")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(1)).withOptions(options)
                    .withLimitToOptions(true).build());
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

            logger.debug("{}: Update OnOff configuration property {}->{} ({})", onoffCluster.getZigBeeAddress(),
                    configurationParameter.getKey(), configurationParameter.getValue(),
                    configurationParameter.getValue().getClass().getSimpleName());
            Integer response = null;
            switch (configurationParameter.getKey()) {
                case CONFIG_OFFWAITTIME:
                    response = configureAttribute(ZclOnOffCluster.ATTR_OFFWAITTIME, configurationParameter.getValue());
                    break;
                case CONFIG_ONTIME:
                    response = configureAttribute(ZclOnOffCluster.ATTR_ONTIME, configurationParameter.getValue());
                    break;
                case CONFIG_STARTUPONOFF:
                    response = configureAttribute(ZclOnOffCluster.ATTR_STARTUPONOFF, configurationParameter.getValue());
                    break;
                default:
                    logger.warn("{}: Unhandled configuration property {}", onoffCluster.getZigBeeAddress(),
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
        ZclAttribute attribute = onoffCluster.getAttribute(attributeId);
        attribute.writeValue(((BigDecimal) (value)).intValue());
        return (Integer) attribute.readValue(0);
    }
}
