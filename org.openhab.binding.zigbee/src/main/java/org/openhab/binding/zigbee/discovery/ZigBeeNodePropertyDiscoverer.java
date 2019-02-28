/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.discovery;

import static org.eclipse.smarthome.core.thing.Thing.PROPERTY_FIRMWARE_VERSION;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Thing;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor;

/**
 * Implements a reusable method to return a set of properties about the device.
 *
 * @author Chris Jackson - initial contribution
 * @author Henning Sudbrock - read multiple attributes with a single command
 */
public class ZigBeeNodePropertyDiscoverer {

    private final Logger logger = LoggerFactory.getLogger(ZigBeeNodePropertyDiscoverer.class);

    private static final int MAX_RETRIES = 3;

    private Map<String, String> properties = new HashMap<String, String>();

    private boolean alwaysUpdate = false;

    /**
     * Sets initial properties
     *
     * @param property The name of the property
     * @param value    The value of the property; if set to null the property will be removed
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

        if (alwaysUpdate || properties.get(Thing.PROPERTY_VENDOR) == null) {
            for (int retry = 0; retry < MAX_RETRIES; retry++) {
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
            for (int retry = 0; retry < MAX_RETRIES; retry++) {
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

    }

    private void addPropertiesFromOtaCluster(ZigBeeNode node) {
        ZclOtaUpgradeCluster otaCluster = (ZclOtaUpgradeCluster) node.getEndpoints().stream()
                .map(ep -> ep.getOutputCluster(ZclOtaUpgradeCluster.CLUSTER_ID)).filter(Objects::nonNull).findFirst()
                .orElse(null);

        if (otaCluster != null) {
            logger.debug("{}: ZigBee node property discovery using ota cluster on endpoint {}", node.getIeeeAddress(),
                    otaCluster.getZigBeeAddress());

            Integer fileVersion = otaCluster.getCurrentFileVersion(Long.MAX_VALUE);
            if (fileVersion != null) {
                properties.put(PROPERTY_FIRMWARE_VERSION, String.format("0x%08X", fileVersion));
            } else {
                logger.debug("{}: Could not get OTA firmware version from device", node.getIeeeAddress());
            }
        } else {
            logger.debug("{}: Node doesn't support ota cluster", node.getIeeeAddress());
        }
    }

}
