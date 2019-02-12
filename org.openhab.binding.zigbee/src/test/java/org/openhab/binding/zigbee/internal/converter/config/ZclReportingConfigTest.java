/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.config;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.Configuration;
import org.junit.Test;
import org.mockito.Mockito;

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

        ZclReportingConfig config = new ZclReportingConfig();
        config.initialize(cluster);
        List<ConfigDescriptionParameter> configuration = config.getConfiguration();

        assertEquals(3, configuration.size());
        ConfigDescriptionParameter cfg = configuration.get(0);
        assertEquals(CONFIG_REPORTINGMIN, cfg.getName());
    }

    @Test
    public void getConfigurationAnalogue() {
        ZclLevelControlCluster cluster = Mockito.mock(ZclLevelControlCluster.class);

        ZclReportingConfig config = new ZclReportingConfig();
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

        ZclReportingConfig config = new ZclReportingConfig();
        config.initialize(cluster);
        config.getConfiguration();

        Configuration configuration = new Configuration();

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
