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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.core.config.core.ConfigDescriptionParameter;

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
