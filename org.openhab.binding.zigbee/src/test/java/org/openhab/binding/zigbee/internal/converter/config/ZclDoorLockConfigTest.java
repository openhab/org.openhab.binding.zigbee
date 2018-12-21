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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.zsmartsystems.zigbee.zcl.clusters.ZclDoorLockCluster;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZclDoorLockConfigTest {
    @Test
    public void getConfiguration() {
        ZclDoorLockCluster cluster = Mockito.mock(ZclDoorLockCluster.class);
        Mockito.when(cluster.discoverAttributes(ArgumentMatchers.anyBoolean())).thenReturn(new MockedBooleanFuture());
        Mockito.when(cluster.isAttributeSupported(ArgumentMatchers.anyInt())).thenReturn(true);

        ZclDoorLockConfig config = new ZclDoorLockConfig();
        config.initialize(cluster);
        List<ConfigDescriptionParameter> configuration = config.getConfiguration();

        assertEquals(4, configuration.size());
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
