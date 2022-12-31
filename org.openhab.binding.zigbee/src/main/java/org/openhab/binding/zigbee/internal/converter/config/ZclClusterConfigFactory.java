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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclDoorLockCluster;

/**
 * A factory that provides configuration handlers for device level clusters.
 * This is only applicable for clusters that are not specific to a channel. For example, the
 * {@link ZclLevelControlConfig} class is directly relevant to a specific dimmer channel. For a lock however, lock
 * configuration data such as keypad volume is more directly applicable to the device itself.
 *
 * @author Chris Jackson
 *
 */
public class ZclClusterConfigFactory {
    private Logger logger = LoggerFactory.getLogger(ZclClusterConfigFactory.class);

    /**
     * Map of all channels supported by the binding
     */
    private final Map<Integer, Class<? extends ZclClusterConfigHandler>> configMap;

    public ZclClusterConfigFactory() {
        configMap = new HashMap<>();

        configMap.put(ZclDoorLockCluster.CLUSTER_ID, ZclDoorLockConfig.class);
    }

    public List<ZclClusterConfigHandler> getConfigHandlers(ZigBeeEndpoint endpoint) {
        List<ZclClusterConfigHandler> handlers = new ArrayList<>();

        for (int clusterId : endpoint.getInputClusterIds()) {
            try {
                ZclClusterConfigHandler handler = getClusterConfigHandler(endpoint.getInputCluster(clusterId));
                if (handler != null) {
                    handlers.add(handler);
                }
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                logger.debug("{}: Exception while getting config for input cluster {}: ", endpoint.getIeeeAddress(),
                        clusterId, e);
            }
        }

        for (int clusterId : endpoint.getOutputClusterIds()) {
            try {
                ZclClusterConfigHandler handler = getClusterConfigHandler(endpoint.getOutputCluster(clusterId));
                if (handler != null) {
                    handlers.add(handler);
                }
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                logger.debug("{}: Exception while getting config for output cluster {}: ", endpoint.getIeeeAddress(),
                        clusterId, e);
            }
        }

        return handlers;
    }

    @SuppressWarnings("unchecked")
    private ZclClusterConfigHandler getClusterConfigHandler(ZclCluster cluster)
            throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        Constructor<? extends ZclClusterConfigHandler> constructor;
        Class<?> converterClass = configMap.get(cluster.getClusterId());
        if (converterClass == null) {
            return null;
        }

        constructor = (Constructor<? extends ZclClusterConfigHandler>) converterClass.getConstructor();
        ZclClusterConfigHandler converter = constructor.newInstance();

        if (converter.initialize(cluster)) {
            return converter;
        }

        return null;
    }

}
