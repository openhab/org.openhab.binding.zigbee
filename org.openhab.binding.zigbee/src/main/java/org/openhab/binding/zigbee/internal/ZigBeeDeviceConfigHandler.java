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
package org.openhab.binding.zigbee.internal;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.core.config.core.Configuration;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Class to handle ZigBee device configuration. This is a helper class used by the {@link ZigBeeThingHandler} for
 * managing device configuration.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeDeviceConfigHandler {
    /**
     * Our logger
     */
    private final Logger logger = LoggerFactory.getLogger(ZigBeeDeviceConfigHandler.class);

    private final static String CONFIG_TYPE_ATTRIBUTE = "attribute";
    private final ZigBeeNode node;

    public ZigBeeDeviceConfigHandler(ZigBeeNode node) {
        this.node = node;
    }

    /**
     * Processes the updated configuration. As required, the method shall process each known configuration parameter and
     * set a local variable for local parameters, and update the remote device for remote parameters.
     * The currentConfiguration shall be updated.
     *
     * @param currentConfiguration the current {@link Configuration}
     * @param updatedParameters a map containing the updated configuration parameters to be set
     */
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {

        for (Entry<String, Object> configurationParameter : updatedParameters.entrySet()) {
            // Ignore any configuration parameters that have not changed
            if (Objects.equals(configurationParameter.getValue(),
                    currentConfiguration.get(configurationParameter.getKey()))) {
                logger.debug("Configuration update: Ignored {} as no change", configurationParameter.getKey());
                continue;
            }

            // Since there is no ability to parameterise configuration parameters from
            // the device perspective, we need to embed the properties into the key
            // eg. attribute_02_in_0406_0030_18 = endpoint_direction_cluster_attribute_datatype
            String[] cfg = configurationParameter.getKey().split("_");
            switch (cfg[0]) {
                case CONFIG_TYPE_ATTRIBUTE:
                    if (cfg.length < 6) {
                        logger.warn("Config {} has insufficient parts", configurationParameter.getKey());
                        break;
                    }
                    int endpointId = Integer.parseInt(cfg[1], 16);
                    boolean direction = "in".equals(cfg[2]);
                    int clusterId = Integer.parseInt(cfg[3], 16);
                    int attributeId = Integer.parseInt(cfg[4], 16);
                    int dataTypeId = Integer.parseInt(cfg[5], 16);
                    updateAttribute(endpointId, direction, clusterId, attributeId, dataTypeId,
                            configurationParameter.getValue());
                    currentConfiguration.put(configurationParameter.getKey(), configurationParameter.getValue());
                    break;
            }
        }
    }

    /**
     * Writes an attribute to the specified endpoint:cluster in the device. This method allows writing of any attribute
     * ID, in any supported cluster - ie the attribute ID doesn't need to be one that the framework knows about.
     *
     * @param endpointId the endpoint number
     * @param direction the direction (true = input cluster, false = output cluster)
     * @param clusterId the cluster number
     * @param attributeId the attribute number
     * @param dataTypeId the data type of the object
     * @param value the object to write
     */
    private void updateAttribute(int endpointId, boolean direction, int clusterId, int attributeId, int dataTypeId,
            Object value) {
        ZigBeeEndpoint endpoint = node.getEndpoint(endpointId);
        ZclCluster cluster;
        if (direction) {
            cluster = endpoint.getInputCluster(clusterId);
        } else {
            cluster = endpoint.getOutputCluster(clusterId);
        }
        if (cluster == null) {
            logger.debug("{}: Cluster {} not found", node.getIeeeAddress(), clusterId);
            return;
        }
        ZclDataType dataType = ZclDataType.getType(dataTypeId);
        if (dataType == null) {
            logger.debug("{}: Data type {} not found", node.getIeeeAddress(), dataType);
            return;
        }
        cluster.write(attributeId, dataType, value);
    }
}
