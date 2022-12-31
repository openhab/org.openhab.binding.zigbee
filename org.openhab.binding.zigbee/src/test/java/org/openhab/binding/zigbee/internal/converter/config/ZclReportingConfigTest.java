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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Channel;

import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZclReportingConfigTest {
    private final String CONFIG_REPORTINGMIN = "zigbee_reporting_min";
    private final String CONFIG_REPORTINGMAX = "zigbee_reporting_max";
    private final String CONFIG_REPORTINGCHANGE = "zigbee_reporting_change";

    @Test
    public void getConfiguration() {
        ZclLevelControlCluster cluster = Mockito.mock(ZclLevelControlCluster.class);

        Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.getConfiguration()).thenReturn(Mockito.mock(Configuration.class));
        ZclReportingConfig config = new ZclReportingConfig(channel);
        config.initialize(cluster);
        List<ConfigDescriptionParameter> configuration = config.getConfiguration();

        assertEquals(3, configuration.size());
        ConfigDescriptionParameter cfg = configuration.get(0);
        assertEquals(CONFIG_REPORTINGMIN, cfg.getName());
    }

    @Test
    public void getConfigurationAnalogue() {
        ZclLevelControlCluster cluster = Mockito.mock(ZclLevelControlCluster.class);

        Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.getConfiguration()).thenReturn(Mockito.mock(Configuration.class));
        ZclReportingConfig config = new ZclReportingConfig(channel);
        config.initialize(cluster);

        config.setAnalogue(BigDecimal.valueOf(1), BigDecimal.valueOf(2), BigDecimal.valueOf(3));
        List<ConfigDescriptionParameter> configuration = config.getConfiguration();

        assertEquals(4, configuration.size());
        ConfigDescriptionParameter cfg = configuration.get(2);
        assertEquals(CONFIG_REPORTINGCHANGE, cfg.getName());
        assertEquals("1", cfg.getDefault());
        assertEquals(BigDecimal.valueOf(2), cfg.getMinimum());
        assertEquals(BigDecimal.valueOf(3), cfg.getMaximum());
    }

    @Test
    public void setReportingTime() {
        ZclLevelControlCluster cluster = Mockito.mock(ZclLevelControlCluster.class);

        Channel channel = Mockito.mock(Channel.class);
        Configuration configuration = new Configuration();
        Map<String, Object> properties = new HashMap<>();
        properties.put("zigbee_reporting_min", BigDecimal.valueOf(12));
        properties.put("zigbee_reporting_max", BigDecimal.valueOf(34));
        properties.put("zigbee_reporting_change", BigDecimal.valueOf(56));
        configuration.setProperties(properties);
        Mockito.when(channel.getConfiguration()).thenReturn(configuration);
        ZclReportingConfig config = new ZclReportingConfig(channel);
        config.initialize(cluster);
        config.getConfiguration();

        configuration = new Configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(CONFIG_REPORTINGMIN, new BigDecimal(45));
        parameters.put(CONFIG_REPORTINGMAX, new BigDecimal(95));
        parameters.put(CONFIG_REPORTINGCHANGE, new BigDecimal(951));

        config.updateConfiguration(configuration, parameters);

        assertEquals(45, config.getReportingTimeMin());
        assertEquals(95, config.getReportingTimeMax());
        assertEquals(951, config.getReportingChange());
    }
}
