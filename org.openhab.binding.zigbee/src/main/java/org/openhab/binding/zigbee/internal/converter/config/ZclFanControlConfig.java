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
import com.zsmartsystems.zigbee.zcl.clusters.ZclFanControlCluster;

/**
 * Configuration handler for the {@link ZclFanControlConfig}
 *
 * @author Chris Jackson
 *
 */
public class ZclFanControlConfig implements ZclClusterConfigHandler {
    private Logger logger = LoggerFactory.getLogger(ZclFanControlConfig.class);

    private static final String CONFIG_ID = "zigbee_fancontrol_";
    private static final String CONFIG_MODESEQUENCE = CONFIG_ID + "modesequence";

    private ZclFanControlCluster fanControlCluster;

    private final List<ConfigDescriptionParameter> parameters = new ArrayList<>();

    @Override
    public boolean initialize(ZclCluster cluster) {
        fanControlCluster = (ZclFanControlCluster) cluster;
        try {
            Boolean result = fanControlCluster.discoverAttributes(false).get();
            if (!result) {
                logger.debug("{}: Unable to get supported attributes for {}.", fanControlCluster.getZigBeeAddress(),
                        fanControlCluster.getClusterName());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Error getting supported attributes for {}. ", fanControlCluster.getZigBeeAddress(),
                    fanControlCluster.getClusterName(), e);
        }

        // Build a list of configuration supported by this channel based on the attributes the cluster supports
        List<ParameterOption> options = new ArrayList<>();
        if (fanControlCluster.isAttributeSupported(ZclFanControlCluster.ATTR_FANMODESEQUENCE)) {
            options.add(new ParameterOption("0", "Low/Med/High"));
            options.add(new ParameterOption("1", "Low/High"));
            options.add(new ParameterOption("2", "Low/Med/High/Auto"));
            options.add(new ParameterOption("3", "Low/High/Auto"));
            options.add(new ParameterOption("4", "On/Auto"));

            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_MODESEQUENCE, Type.INTEGER)
                    .withLabel("Fan Mode Sequence").withDescription("Possible fan modes that may be selected")
                    .withOptions(options).withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(4)).build());
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
                    fanControlCluster.getZigBeeAddress(), configurationParameter.getKey(),
                    configurationParameter.getValue(), configurationParameter.getValue().getClass().getSimpleName());
            Integer response = null;
            switch (configurationParameter.getKey()) {
                case CONFIG_MODESEQUENCE:
                    response = configureAttribute(ZclFanControlCluster.ATTR_FANMODESEQUENCE,
                            configurationParameter.getValue());
                    break;
                default:
                    logger.warn("{}: Unhandled configuration property {}", fanControlCluster.getZigBeeAddress(),
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
        ZclAttribute attribute = fanControlCluster.getAttribute(attributeId);
        attribute.writeValue(((BigDecimal) (value)).intValue());
        return (Integer) attribute.readValue(0);
    }
}
