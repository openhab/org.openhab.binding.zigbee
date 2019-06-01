/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.Configuration;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZclLevelControlConfigTest {
    private final String CONFIG_DEFAULTTRANSITIONTIME = "zigbee_levelcontrol_transitiontimedefault";

    @Test
    public void getConfiguration() {
        ZclLevelControlCluster cluster = Mockito.mock(ZclLevelControlCluster.class);
        Mockito.when(cluster.discoverAttributes(ArgumentMatchers.anyBoolean())).thenReturn(new MockedBooleanFuture());
        Mockito.when(cluster.isAttributeSupported(ArgumentMatchers.anyInt())).thenReturn(true);

        ZclLevelControlConfig config = new ZclLevelControlConfig();
        config.initialize(cluster);
        List<ConfigDescriptionParameter> configuration = config.getConfiguration();

        assertEquals(6, configuration.size());
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
