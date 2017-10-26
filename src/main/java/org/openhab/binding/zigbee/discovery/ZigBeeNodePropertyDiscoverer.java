package org.openhab.binding.zigbee.discovery;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor;

/**
 * Implements a reusable method to return a set of properties about the device
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeNodePropertyDiscoverer {
    private Logger logger = LoggerFactory.getLogger(ZigBeeNodePropertyDiscoverer.class);

    public Map<String, String> getProperties(final ZigBeeCoordinatorHandler coordinatorHandler, final ZigBeeNode node) {

        logger.debug("{}: ZigBee node property discovery start", node.getIeeeAddress());

        // Create a list of devices for the discovery service to work with
        Collection<ZigBeeEndpoint> devices = coordinatorHandler.getNodeEndpoints(node.getIeeeAddress());

        // Make sure we found some devices!
        if (devices.size() == 0) {
            logger.debug("{}: Node has no devices", node.getIeeeAddress());
            return null;
        }

        // Find a device that supports the BASIC cluster and get device information
        Map<String, String> properties = new HashMap<String, String>();
        ZclBasicCluster basicCluster = null;

        for (ZigBeeEndpoint device : devices) {
            basicCluster = (ZclBasicCluster) device.getCluster(ZclBasicCluster.CLUSTER_ID);
            if (basicCluster != null) {
                break;
            }
        }

        if (basicCluster == null) {
            logger.debug("{}: Node doesn't support basic cluster", node.getIeeeAddress());
            return null;
        }

        logger.debug("{}: ZigBee node property discovery using {}", node.getIeeeAddress(),
                basicCluster.getZigBeeAddress());

        String manufacturer = basicCluster.getManufacturerName(Long.MAX_VALUE);
        if (manufacturer != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_MANUFACTURER, manufacturer);
        } else {
            logger.debug("{}: Manufacturer request timeout", node.getIeeeAddress());
        }

        String model = basicCluster.getModelIdentifier(Long.MAX_VALUE);
        if (model != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_MODEL, model);
        } else {
            logger.debug("{}: Model request timeout", node.getIeeeAddress());
        }
        Integer hwVersion = basicCluster.getHwVersion(Long.MAX_VALUE);
        if (hwVersion != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_HWVERSION, hwVersion.toString());
        } else {
            logger.debug("{}: Hardware version request timeout", node.getIeeeAddress());
        }

        Integer stkVersion = basicCluster.getStackVersion(Long.MAX_VALUE);
        if (stkVersion != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_STKVERSION, stkVersion.toString());
        } else {
            logger.debug("{}: Stack version request timeout", node.getIeeeAddress());
        }

        Integer zclVersion = basicCluster.getZclVersion(Long.MAX_VALUE);
        if (zclVersion != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_ZCLVERSION, zclVersion.toString());
        } else {
            logger.debug("{}: ZCL version request timeout", node.getIeeeAddress());
        }

        Integer appVersion = basicCluster.getApplicationVersion(Long.MAX_VALUE);
        if (appVersion != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_APPVERSION, appVersion.toString());
        } else {
            logger.debug("{}: Application version request timeout", node.getIeeeAddress());
        }

        String dateCode = basicCluster.getDateCode(Long.MAX_VALUE);
        if (dateCode != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_DATECODE, dateCode);
        } else {
            logger.debug("{}: Date code request timeout", node.getIeeeAddress());
        }

        if (node.getLogicalType() != null) {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_LOGICALTYPE, node.getLogicalType().toString());
        }
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_NETWORKADDRESS, node.getNetworkAddress().toString());

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
