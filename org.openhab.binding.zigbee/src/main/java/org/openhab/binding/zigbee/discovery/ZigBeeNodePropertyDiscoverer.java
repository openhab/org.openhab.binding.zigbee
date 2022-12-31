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
package org.openhab.binding.zigbee.discovery;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster.*;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;
import static org.openhab.core.thing.Thing.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.core.thing.Thing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor;

/**
 * Implements a reusable method to return a set of properties about the device.
 *
 * @author Chris Jackson - initial contribution
 * @author Henning Sudbrock - read multiple attributes from basic cluster with a single command to speedup discovery
 */
public class ZigBeeNodePropertyDiscoverer {

    private final Logger logger = LoggerFactory.getLogger(ZigBeeNodePropertyDiscoverer.class);

    private static final Map<String, Integer> BASIC_CLUSTER_ATTRIBUTES_FOR_THING_PROPERTY;
    static {
        Map<String, Integer> map = new HashMap<String, Integer>();
        map.put(PROPERTY_VENDOR, ATTR_MANUFACTURERNAME);
        map.put(PROPERTY_MODEL_ID, ATTR_MODELIDENTIFIER);
        map.put(PROPERTY_HARDWARE_VERSION, ATTR_HWVERSION);
        map.put(THING_PROPERTY_APPLICATIONVERSION, ATTR_APPLICATIONVERSION);
        map.put(THING_PROPERTY_STKVERSION, ATTR_STACKVERSION);
        map.put(THING_PROPERTY_ZCLVERSION, ATTR_ZCLVERSION);
        map.put(THING_PROPERTY_DATECODE, ATTR_DATECODE);
        BASIC_CLUSTER_ATTRIBUTES_FOR_THING_PROPERTY = Collections.unmodifiableMap(map);
    }

    private Map<String, String> properties = new HashMap<String, String>();

    private boolean alwaysUpdate = false;

    /**
     * Sets initial properties
     *
     * @param property The name of the property
     * @param value The value of the property; if set to null the property will be removed
     */
    public void setProperty(@NonNull String property, @Nullable String value) {
        if (value == null) {
            properties.remove(property);
        } else {
            properties.put(property, value);
        }
    }

    /**
     * Sets the initial properties to be updated
     *
     * @param properties The properties to be updated
     */
    public void setProperties(@NonNull Map<@NonNull String, @NonNull String> properties) {
        this.properties.putAll(properties);
    }

    /**
     * If alwaysUpdate is true, then properties will be updated even if they are known.
     *
     * @param alwaysUpdate
     */
    public void setAlwaysUpdate(boolean alwaysUpdate) {
        this.alwaysUpdate = alwaysUpdate;
    }

    /**
     * Gets the properties from the device
     *
     * @param node the {@link ZigBeeNode}
     * @return a {@link Map} of properties or an empty map if there was an error
     */
    public Map<String, String> getProperties(final ZigBeeNode node) {
        logger.debug("{}: ZigBee node property discovery start", node.getIeeeAddress());

        addPropertiesFromNodeDescriptors(node);
        addPropertiesFromBasicCluster(node);
        addPropertiesFromOtaCluster(node);

        logger.debug("{}: ZigBee node property discovery complete: {}", node.getIeeeAddress(), properties);

        return properties;
    }

    private void addPropertiesFromNodeDescriptors(ZigBeeNode node) {
        if (node.getLogicalType() != null) {
            properties.put(THING_PROPERTY_LOGICALTYPE, node.getLogicalType().toString());
        }

        properties.put(THING_PROPERTY_NETWORKADDRESS, node.getNetworkAddress().toString());

        PowerDescriptor powerDescriptor = node.getPowerDescriptor();
        if (powerDescriptor != null) {
            properties.put(THING_PROPERTY_AVAILABLEPOWERSOURCES, powerDescriptor.getAvailablePowerSources().toString());
            properties.put(THING_PROPERTY_POWERSOURCE, powerDescriptor.getCurrentPowerSource().toString());
            properties.put(THING_PROPERTY_POWERMODE, powerDescriptor.getCurrentPowerMode().toString());
            properties.put(THING_PROPERTY_POWERLEVEL, powerDescriptor.getPowerLevel().toString());
        }

        if (node.getNodeDescriptor() != null) {
            properties.put(THING_PROPERTY_MANUFACTURERCODE,
                    String.format("0x%04x", node.getNodeDescriptor().getManufacturerCode()));
        }
    }

    private void addPropertiesFromBasicCluster(ZigBeeNode node) {
        ZclBasicCluster basicCluster = (ZclBasicCluster) node.getEndpoints().stream()
                .map(ep -> ep.getInputCluster(ZclBasicCluster.CLUSTER_ID)).filter(Objects::nonNull).findFirst()
                .orElse(null);

        if (basicCluster == null) {
            logger.debug("{}: Node doesn't support basic cluster", node.getIeeeAddress());
            return;
        }

        logger.debug("{}: ZigBee node property discovery using basic cluster on endpoint {}", node.getIeeeAddress(),
                basicCluster.getZigBeeAddress());

        // Attempt to read all properties with a single command.
        // If successful, this updates the cache with the property values.
        try {
            Map<String, Integer> propertiesToRead = getPropertiesToRead(basicCluster);
            List<Integer> attributes = new ArrayList<>(propertiesToRead.values());
            if (!attributes.isEmpty()) {
                basicCluster.readAttributes(attributes).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.info("{}: There was an error when trying to read all properties with a single command.",
                    node.getIeeeAddress(), e);
        }

        // Now, get each single property via the basic cluster. If the above multi-attribute read was successful,
        // this will get each property from the cache. Otherwise, it will try to get the property from the device again.

        if (alwaysUpdate || properties.get(Thing.PROPERTY_VENDOR) == null) {
            String manufacturer = basicCluster.getManufacturerName(Long.MAX_VALUE);
            if (manufacturer != null) {
                properties.put(Thing.PROPERTY_VENDOR, manufacturer.trim());
            } else {
                logger.debug("{}: Manufacturer request failed", node.getIeeeAddress());
            }
        }

        if (alwaysUpdate || properties.get(Thing.PROPERTY_MODEL_ID) == null) {
            String model = basicCluster.getModelIdentifier(Long.MAX_VALUE);
            if (model != null) {
                properties.put(Thing.PROPERTY_MODEL_ID, model.trim());
            } else {
                logger.debug("{}: Model request failed", node.getIeeeAddress());
            }
        }

        if (alwaysUpdate || properties.get(Thing.PROPERTY_HARDWARE_VERSION) == null) {
            Integer hwVersion = basicCluster.getHwVersion(Long.MAX_VALUE);
            if (hwVersion != null) {
                properties.put(Thing.PROPERTY_HARDWARE_VERSION, hwVersion.toString());
            } else {
                logger.debug("{}: Hardware version failed", node.getIeeeAddress());
            }
        }

        if (alwaysUpdate || properties.get(ZigBeeBindingConstants.THING_PROPERTY_APPLICATIONVERSION) == null) {
            Integer appVersion = basicCluster.getApplicationVersion(Long.MAX_VALUE);
            if (appVersion != null) {
                properties.put(ZigBeeBindingConstants.THING_PROPERTY_APPLICATIONVERSION, appVersion.toString());
            } else {
                logger.debug("{}: Application version failed", node.getIeeeAddress());
            }
        }

        if (alwaysUpdate || properties.get(ZigBeeBindingConstants.THING_PROPERTY_STKVERSION) == null) {
            Integer stkVersion = basicCluster.getStackVersion(Long.MAX_VALUE);
            if (stkVersion != null) {
                properties.put(ZigBeeBindingConstants.THING_PROPERTY_STKVERSION, stkVersion.toString());
            } else {
                logger.debug("{}: Stack version failed", node.getIeeeAddress());
            }
        }

        if (alwaysUpdate || properties.get(ZigBeeBindingConstants.THING_PROPERTY_ZCLVERSION) == null) {
            Integer zclVersion = basicCluster.getZclVersion(Long.MAX_VALUE);
            if (zclVersion != null) {
                properties.put(ZigBeeBindingConstants.THING_PROPERTY_ZCLVERSION, zclVersion.toString());
            } else {
                logger.debug("{}: ZCL version failed", node.getIeeeAddress());
            }
        }

        if (alwaysUpdate || properties.get(ZigBeeBindingConstants.THING_PROPERTY_DATECODE) == null) {
            String dateCode = basicCluster.getDateCode(Long.MAX_VALUE);
            if (dateCode != null) {
                properties.put(ZigBeeBindingConstants.THING_PROPERTY_DATECODE, dateCode);
            } else {
                logger.debug("{}: Date code failed", node.getIeeeAddress());
            }
        }

    }

    private Map<String, Integer> getPropertiesToRead(ZclBasicCluster basicCluster) {
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : BASIC_CLUSTER_ATTRIBUTES_FOR_THING_PROPERTY.entrySet()) {
            if (alwaysUpdate || properties.get(entry.getKey()) == null
                    || !basicCluster.getAttribute(entry.getValue()).isLastValueCurrent(Long.MAX_VALUE)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private void addPropertiesFromOtaCluster(ZigBeeNode node) {
        ZclOtaUpgradeCluster otaCluster = (ZclOtaUpgradeCluster) node.getEndpoints().stream()
                .map(ep -> ep.getOutputCluster(ZclOtaUpgradeCluster.CLUSTER_ID)).filter(Objects::nonNull).findFirst()
                .orElse(null);

        if (otaCluster != null) {
            logger.debug("{}: ZigBee node property discovery using OTA cluster on endpoint {}", node.getIeeeAddress(),
                    otaCluster.getZigBeeAddress());

            ZclAttribute attribute = otaCluster.getAttribute(ZclOtaUpgradeCluster.ATTR_CURRENTFILEVERSION);
            Object fileVersion = attribute.readValue(Long.MAX_VALUE);
            if (fileVersion != null) {
                properties.put(PROPERTY_FIRMWARE_VERSION, String.format("0x%08X", fileVersion));
            } else {
                logger.debug("{}: Could not get OTA firmware version from device", node.getIeeeAddress());
            }
        } else {
            logger.debug("{}: Node doesn't support OTA cluster", node.getIeeeAddress());
        }
    }

}
