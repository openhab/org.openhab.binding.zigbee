/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.discovery;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Thing;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor;

/**
 * Implements a reusable method to return a set of properties about the device
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeNodePropertyDiscoverer {
    private Logger logger = LoggerFactory.getLogger(ZigBeeNodePropertyDiscoverer.class);

    private Map<String, String> properties = new HashMap<String, String>();

    private int maxRetries = 3;
    private boolean alwaysUpdate = false;

    /**
     * Sets initial properties
     *
     * @param property
     * @param value If set to null the property will be removed
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
     * @param properties
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

        // Create a list of endpoints for the discovery service to work with
        Collection<ZigBeeEndpoint> endpoints = node.getEndpoints();

        // Make sure the device has some endpoints!
        if (endpoints.size() == 0) {
            logger.debug("{}: Node has no endpoints", node.getIeeeAddress());
            return properties;
        }

        // Find an endpoint that supports the BASIC cluster and get device information
        ZclBasicCluster basicCluster = null;

        for (ZigBeeEndpoint device : endpoints) {
            basicCluster = (ZclBasicCluster) device.getInputCluster(ZclBasicCluster.CLUSTER_ID);
            if (basicCluster != null) {
                break;
            }
        }

        if (basicCluster == null) {
            logger.debug("{}: Node doesn't support basic cluster", node.getIeeeAddress());
            return properties;
        }

        logger.debug("{}: ZigBee node property discovery using {}", node.getIeeeAddress(),
                basicCluster.getZigBeeAddress());

        if (alwaysUpdate || properties.get(Thing.PROPERTY_VENDOR) == null) {
            for (int retry = 0; retry < maxRetries; retry++) {
                String manufacturer = basicCluster.getManufacturerName(Long.MAX_VALUE);
                if (manufacturer != null) {
                    properties.put(Thing.PROPERTY_VENDOR, manufacturer.trim());
                    break;
                } else {
                    logger.debug("{}: Manufacturer request failed (retry {})", node.getIeeeAddress(), retry);
                }
            }
        }

        if (alwaysUpdate || properties.get(Thing.PROPERTY_MODEL_ID) == null) {
            for (int retry = 0; retry < maxRetries; retry++) {
                String model = basicCluster.getModelIdentifier(Long.MAX_VALUE);
                if (model != null) {
                    properties.put(Thing.PROPERTY_MODEL_ID, model.trim());
                    break;
                } else {
                    logger.debug("{}: Model request failed (retry {})", node.getIeeeAddress(), retry);
                }
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

        if (node.getLogicalType() != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_LOGICALTYPE, node.getLogicalType().toString());
        }
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_NETWORKADDRESS, node.getNetworkAddress().toString());

        // Find an OTA client if the device supports OTA upgrades
        ZclOtaUpgradeCluster otaCluster = null;
        for (ZigBeeEndpoint endpoint : node.getEndpoints()) {
            otaCluster = (ZclOtaUpgradeCluster) endpoint.getOutputCluster(ZclOtaUpgradeCluster.CLUSTER_ID);
            if (otaCluster != null) {
                break;
            }
        }

        if (otaCluster != null) {
            Integer fileVersion = otaCluster.getCurrentFileVersion(Long.MAX_VALUE);
            if (fileVersion != null) {
                properties.put(Thing.PROPERTY_FIRMWARE_VERSION,
                        String.format("%s%08X", ZigBeeBindingConstants.FIRMWARE_VERSION_HEX_PREFIX, fileVersion));
            } else {
                logger.debug("{}: OTA firmware failed", node.getIeeeAddress());
            }
        }

        PowerDescriptor powerDescriptor = node.getPowerDescriptor();
        if (powerDescriptor != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_AVAILABLEPOWERSOURCES,
                    powerDescriptor.getAvailablePowerSources().toString());
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_POWERSOURCE,
                    powerDescriptor.getCurrentPowerSource().toString());
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_POWERMODE,
                    powerDescriptor.getCurrentPowerMode().toString());
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_POWERLEVEL,
                    powerDescriptor.getPowerLevel().toString());
        }

        logger.debug("{}: ZigBee node property discovery complete: {}", node.getIeeeAddress(), properties);

        return properties;
    }

}
