/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

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
     * Handles a device configuration update
     *
     * @param configuration map of configuration parameters
     * @return a map of updated configuration parameters
     */
    public Map<String, Object> handleConfigurationUpdate(Map<String, Object> configuration) {
        Map<String, Object> updatedConfiguration = new HashMap<>();

        for (Entry<String, Object> configurationParameter : configuration.entrySet()) {
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
                    updatedConfiguration.put(configurationParameter.getKey(), configurationParameter.getValue());
                    break;
            }
        }

        return updatedConfiguration;
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
