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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.PercentType;

import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZclLevelControlConfigTest {
    private final String CONFIG_DEFAULTTRANSITIONTIME = "zigbee_levelcontrol_transitiontimedefault";
    private final String CONFIG_INVERTCONTROL = "zigbee_levelcontrol_invertcontrol";
    private final String CONFIG_INVERTREPORT = "zigbee_levelcontrol_invertreport";

    @Test
    public void getConfiguration() {
        ZclLevelControlCluster cluster = Mockito.mock(ZclLevelControlCluster.class);
        Mockito.when(cluster.discoverAttributes(ArgumentMatchers.anyBoolean())).thenReturn(new MockedBooleanFuture());
        Mockito.when(cluster.isAttributeSupported(ArgumentMatchers.anyInt())).thenReturn(true);

        ZclLevelControlConfig config = new ZclLevelControlConfig();
        config.initialize(cluster);
        List<ConfigDescriptionParameter> configuration = config.getConfiguration();

        assertEquals(8, configuration.size());
        ConfigDescriptionParameter cfg = configuration.get(0);
        assertEquals(CONFIG_DEFAULTTRANSITIONTIME, cfg.getName());
    }

    @Test
    public void getDefaultTransitionTime() {
        ZclLevelControlCluster cluster = Mockito.mock(ZclLevelControlCluster.class);
        Mockito.when(cluster.discoverAttributes(ArgumentMatchers.anyBoolean())).thenReturn(new MockedBooleanFuture());
        Mockito.when(cluster.isAttributeSupported(ArgumentMatchers.anyInt())).thenReturn(true);

        ZclLevelControlConfig config = new ZclLevelControlConfig();
        config.initialize(cluster);
        config.getConfiguration();

        Configuration configuration = new Configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(CONFIG_DEFAULTTRANSITIONTIME, new BigDecimal(45));
        config.updateConfiguration(configuration, parameters);

        assertEquals(45, config.getDefaultTransitionTime());
    }

    @Test
    public void handleInvertControl() {
        ZclLevelControlCluster cluster = Mockito.mock(ZclLevelControlCluster.class);
        Mockito.when(cluster.discoverAttributes(ArgumentMatchers.anyBoolean())).thenReturn(new MockedBooleanFuture());
        Mockito.when(cluster.isAttributeSupported(ArgumentMatchers.anyInt())).thenReturn(true);

        ZclLevelControlConfig config = new ZclLevelControlConfig();
        config.initialize(cluster);
        config.getConfiguration();

        assertEquals((new PercentType(75)), config.handleInvertControl(new PercentType(75)));

        Configuration configuration = new Configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(CONFIG_INVERTCONTROL, Boolean.TRUE);
        config.updateConfiguration(configuration, parameters);

        assertEquals((new PercentType(25)), config.handleInvertControl(new PercentType(75)));
        assertEquals((new PercentType(0)), config.handleInvertControl((new PercentType(100))));
        assertEquals((new PercentType(99)), config.handleInvertControl((new PercentType(1))));

        parameters.put(CONFIG_INVERTCONTROL, Boolean.FALSE);
        config.updateConfiguration(configuration, parameters);

        assertEquals((new PercentType(75)), config.handleInvertControl(new PercentType(75)));
        assertEquals((new PercentType(100)), config.handleInvertControl((new PercentType(100))));
        assertEquals((new PercentType(1)), config.handleInvertControl((new PercentType(1))));
    }

    @Test
    public void handleInvertReport() {
        ZclLevelControlCluster cluster = Mockito.mock(ZclLevelControlCluster.class);
        Mockito.when(cluster.discoverAttributes(ArgumentMatchers.anyBoolean())).thenReturn(new MockedBooleanFuture());
        Mockito.when(cluster.isAttributeSupported(ArgumentMatchers.anyInt())).thenReturn(true);

        ZclLevelControlConfig config = new ZclLevelControlConfig();
        config.initialize(cluster);
        config.getConfiguration();

        assertEquals((new PercentType(75)), config.handleInvertReport(new PercentType(75)));

        Configuration configuration = new Configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(CONFIG_INVERTREPORT, Boolean.TRUE);
        config.updateConfiguration(configuration, parameters);

        assertEquals((new PercentType(25)), config.handleInvertReport(new PercentType(75)));
        assertEquals((new PercentType(0)), config.handleInvertReport((new PercentType(100))));
        assertEquals((new PercentType(99)), config.handleInvertReport((new PercentType(1))));

        parameters.put(CONFIG_INVERTREPORT, Boolean.FALSE);
        config.updateConfiguration(configuration, parameters);

        assertEquals((new PercentType(75)), config.handleInvertReport(new PercentType(75)));
        assertEquals((new PercentType(100)), config.handleInvertReport((new PercentType(100))));
        assertEquals((new PercentType(1)), config.handleInvertReport((new PercentType(1))));
    }

    class MockedBooleanFuture implements Future<Boolean> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Boolean get() throws InterruptedException, ExecutionException {
            return Boolean.TRUE;
        }

        @Override
        public Boolean get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }

    }
}
