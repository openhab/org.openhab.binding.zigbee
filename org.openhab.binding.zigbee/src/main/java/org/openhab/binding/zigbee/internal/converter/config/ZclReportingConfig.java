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
import org.openhab.core.thing.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.zcl.ZclCluster;

/**
 * Configuration handler for reporting of digital attributes. This should be applied for each channel - ie if a channel
 * uses multiple attributes, then they should all be configured the same.
 *
 * @author Chris Jackson
 *
 */
public class ZclReportingConfig implements ZclClusterConfigHandler {
    private final Logger logger = LoggerFactory.getLogger(ZclReportingConfig.class);

    private static final Integer REPORTING_RANGE_MIN = 1;
    private static final Integer REPORTING_RANGE_MAX = 86400;

    private static final Integer REPORTING_DEFAULT_MIN = 1;
    private static final Integer REPORTING_DEFAULT_MAX = 900;

    private static final Integer POLLING_RANGE_MIN = 15;
    private static final Integer POLLING_RANGE_MAX = 86400;
    private static final Integer POLLING_DEFAULT = 900;

    private static final String CONFIG_ID = "zigbee_reporting_";
    private static final String CONFIG_REPORTINGMIN = CONFIG_ID + "min";
    private static final String CONFIG_REPORTINGMAX = CONFIG_ID + "max";
    private static final String CONFIG_REPORTINGCHANGE = CONFIG_ID + "change";
    public static final String CONFIG_POLLING = CONFIG_ID + "polling";

    private int reportingTimeMin = REPORTING_DEFAULT_MIN;
    private int reportingTimeMax = REPORTING_DEFAULT_MAX;
    private int reportingChange = 1;
    private int pollingPeriod = POLLING_DEFAULT;

    // Reporting change configuration. These will need to be set for each channel.
    private boolean isAnalogue = false;
    private BigDecimal defaultChange;
    private BigDecimal minimumChange;
    private BigDecimal maximumChange;

    public ZclReportingConfig(Channel channel) {
        Configuration configuration = channel.getConfiguration();
        if (configuration.containsKey(CONFIG_REPORTINGMIN)) {
            reportingTimeMin = ((BigDecimal) configuration.get(CONFIG_REPORTINGMIN)).intValue();
        }
        if (configuration.containsKey(CONFIG_REPORTINGMAX)) {
            reportingTimeMax = ((BigDecimal) configuration.get(CONFIG_REPORTINGMAX)).intValue();
        }
        if (configuration.containsKey(CONFIG_REPORTINGCHANGE)) {
            reportingChange = ((BigDecimal) configuration.get(CONFIG_REPORTINGCHANGE)).intValue();
        }
        if (configuration.containsKey(CONFIG_POLLING)) {
            pollingPeriod = ((BigDecimal) configuration.get(CONFIG_POLLING)).intValue();
        }
    }

    @Override
    public boolean initialize(ZclCluster cluster) {
        return true;
    }

    /**
     * Sets the analogue reporting values
     *
     * @param defaultChange the default value
     * @param minimumChange the minimum reporting value
     * @param maximumChange the maximum reporting value
     */
    public void setAnalogue(BigDecimal defaultChange, BigDecimal minimumChange, BigDecimal maximumChange) {
        this.isAnalogue = true;
        this.defaultChange = defaultChange;
        this.minimumChange = minimumChange;
        this.maximumChange = maximumChange;
    }

    @Override
    public List<ConfigDescriptionParameter> getConfiguration() {
        List<ConfigDescriptionParameter> parameters = new ArrayList<>();

        // Build a list of configuration
        parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_REPORTINGMIN, Type.INTEGER)
                .withLabel("Minimum Reporting Period")
                .withDescription("The minimum time period in seconds between device state updates")
                .withDefault(REPORTING_DEFAULT_MIN.toString()).withMinimum(new BigDecimal(REPORTING_RANGE_MIN))
                .withMaximum(new BigDecimal(REPORTING_RANGE_MAX)).build());

        parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_REPORTINGMAX, Type.INTEGER)
                .withLabel("Maximum Reporting Period")
                .withDescription("The maximum time period in seconds between device state updates")
                .withDefault(REPORTING_DEFAULT_MAX.toString()).withMinimum(new BigDecimal(REPORTING_RANGE_MIN))
                .withMaximum(new BigDecimal(REPORTING_RANGE_MAX)).build());

        if (isAnalogue) {
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_REPORTINGCHANGE, Type.INTEGER)
                    .withLabel("Report On Change")
                    .withDescription(
                            "The minimum change of the attribute value needed to trigger a device state update")
                    .withDefault(defaultChange.toString()).withMinimum(minimumChange).withMaximum(maximumChange)
                    .build());
        }

        parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_POLLING, Type.INTEGER)
                .withLabel("Polling Period").withDescription("The time period in seconds between subsequent polls")
                .withDefault(POLLING_DEFAULT.toString()).withMinimum(new BigDecimal(POLLING_RANGE_MIN))
                .withMaximum(new BigDecimal(POLLING_RANGE_MAX)).withAdvanced(true).build());

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

            switch (configurationParameter.getKey()) {
                case CONFIG_REPORTINGMIN:
                    reportingTimeMin = ((BigDecimal) (configurationParameter.getValue())).intValue();
                    updated = true;
                    break;
                case CONFIG_REPORTINGMAX:
                    reportingTimeMax = ((BigDecimal) (configurationParameter.getValue())).intValue();
                    updated = true;
                    break;
                case CONFIG_REPORTINGCHANGE:
                    reportingChange = ((BigDecimal) (configurationParameter.getValue())).intValue();
                    updated = true;
                    break;
                case CONFIG_POLLING:
                    pollingPeriod = ((BigDecimal) (configurationParameter.getValue())).intValue();
                    updated = true;
                    break;
                default:
                    logger.warn("Unhandled configuration property {}", configurationParameter.getKey());
                    break;
            }
        }

        return updated;
    }

    /**
     * Gets the minimum reporting time in seconds
     *
     * @return the minimum reporting time in seconds
     */
    public int getReportingTimeMin() {
        return reportingTimeMin;
    }

    /**
     * Gets the maximum reporting time in seconds
     *
     * @return the maximum reporting time in seconds
     */
    public int getReportingTimeMax() {
        return reportingTimeMax;
    }

    /**
     * Gets the reporting change configuration
     *
     * @return the reporting change parameter
     */
    public int getReportingChange() {
        return reportingChange;
    }

    /**
     * Gets the polling period configuration
     *
     * @return the polling period parameter
     */
    public int getPollingPeriod() {
        return pollingPeriod;
    }
}
