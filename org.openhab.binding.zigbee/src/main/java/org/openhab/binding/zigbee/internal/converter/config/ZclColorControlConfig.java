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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.ConfigDescriptionParameter.Type;
import org.openhab.core.config.core.ConfigDescriptionParameterBuilder;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.thing.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;

/**
 * Configuration handler for the {@link ZclColorControlCluster}
 *
 * @author Chris Jackson
 *
 */
public class ZclColorControlConfig implements ZclClusterConfigHandler {
    private Logger logger = LoggerFactory.getLogger(ZclColorControlConfig.class);

    private static final String CONFIG_ID = "zigbee_color_";
    private static final String CONFIG_CONTROLMETHOD = CONFIG_ID + "controlmethod";

    private ZclColorControlCluster colorControlCluster;

    public enum ControlMethod {
        AUTO,
        HUE,
        XY
    }

    private ControlMethod controlMethod = ControlMethod.AUTO;

    private final List<ConfigDescriptionParameter> parameters = new ArrayList<>();

    public ZclColorControlConfig(Channel channel) {
        Configuration configuration = channel.getConfiguration();
        if (configuration.containsKey(CONFIG_CONTROLMETHOD)) {
            controlMethod = ControlMethod.valueOf((String) configuration.get(CONFIG_CONTROLMETHOD));
        }
    }

    @Override
    public boolean initialize(ZclCluster cluster) {
        colorControlCluster = (ZclColorControlCluster) cluster;

        // Build a list of configuration supported by this channel
        List<ParameterOption> options = new ArrayList<>();

        options = new ArrayList<>();
        options.add(new ParameterOption(ControlMethod.AUTO.toString(), "Auto"));
        options.add(new ParameterOption(ControlMethod.HUE.toString(), "Hue Commands"));
        options.add(new ParameterOption(ControlMethod.XY.toString(), "XY Commands"));
        parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_CONTROLMETHOD, Type.TEXT)
                .withLabel("Color Control Method")
                .withDescription(
                        "The commands used to control color. AUTO will use HUE if the device supports, otherwise XY")
                .withDefault(ControlMethod.AUTO.toString()).withOptions(options).withLimitToOptions(true).build());

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

            logger.debug("{}: Update ColorControl configuration property {}->{} ({})",
                    colorControlCluster.getZigBeeAddress(), configurationParameter.getKey(),
                    configurationParameter.getValue(), configurationParameter.getValue().getClass().getSimpleName());
            switch (configurationParameter.getKey()) {
                case CONFIG_CONTROLMETHOD:
                    controlMethod = ControlMethod.valueOf((String) configurationParameter.getValue());
                    break;
                default:
                    logger.warn("{}: Unhandled configuration property {}", colorControlCluster.getZigBeeAddress(),
                            configurationParameter.getKey());
                    break;
            }
        }

        return updated;
    }

    public ControlMethod getControlMethod() {
        return controlMethod;
    }
}
