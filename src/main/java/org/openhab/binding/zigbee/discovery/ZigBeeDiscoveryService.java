/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryServiceCallback;
import org.eclipse.smarthome.config.discovery.ExtendedDiscoveryService;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zdo.descriptors.NodeDescriptor.LogicalType;

/**
 * The {@link ZigBeeDiscoveryService} tracks ZigBee devices which are associated
 * to coordinator.
 *
 * @author Chris Jackson - Initial contribution
 *
 */
public class ZigBeeDiscoveryService extends AbstractDiscoveryService implements ExtendedDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeDiscoveryService.class);

    private final static int SEARCH_TIME = 60;

    private final ZigBeeCoordinatorHandler coordinatorHandler;

    private DiscoveryServiceCallback discoveryServiceCallback;

    public ZigBeeDiscoveryService(ZigBeeCoordinatorHandler coordinatorHandler) {
        super(SEARCH_TIME);
        this.coordinatorHandler = coordinatorHandler;
        logger.debug("Creating ZigBee discovery service for {}", coordinatorHandler.getThing().getUID());
    }

    public void activate() {
        logger.debug("Activating ZigBee discovery service for {}", coordinatorHandler.getThing().getUID());
    }

    @Override
    public void setDiscoveryServiceCallback(DiscoveryServiceCallback discoveryServiceCallback) {
        this.discoveryServiceCallback = discoveryServiceCallback;
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return ZigBeeBindingConstants.SUPPORTED_THING_TYPES;
    }

    @Override
    public void deactivate() {
        logger.debug("Deactivating ZigBee discovery service for {}", coordinatorHandler.getThing().getUID());
    }

    @Override
    public void startScan() {
        logger.debug("Starting ZigBee scan for {}", coordinatorHandler.getThing().getUID());

        // Update the inbox with all devices we already know about
        for (ZigBeeNode node : coordinatorHandler.getNodes()) {
            if (node.getLogicalType() == LogicalType.COORDINATOR) {
                continue;
            }
            nodeDiscovered(node);
        }

        // Start the search for new devices
        coordinatorHandler.startDeviceDiscovery();
    }

    /**
     * Adds a new device to the network.
     * This starts a thread to read information about the device so we can
     * create the thingType, and the label for the user.
     *
     * @param node the new {@link ZigBeeNode}
     */
    public void nodeDiscovered(final ZigBeeNode node) {
        Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                logger.info("{}: Starting ZigBee device discovery", node.getIeeeAddress());

                ThingTypeUID thingTypeUID = ZigBeeBindingConstants.THING_TYPE_GENERIC_DEVICE;
                ThingUID bridgeUID = coordinatorHandler.getThing().getUID();

                String thingId = node.getIeeeAddress().toString().toLowerCase().replaceAll("[^a-z0-9_/]", "");
                ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, thingId);

                // Set the default label
                String label = "Unknown ZigBee Device " + node.getIeeeAddress();

                // Add this device to the inbox if we don't already know about it
                // Do this here incase the device doesn't respond to the discovery messages.
                // This keeps the user informed.
                if (discoveryServiceCallback != null
                        && discoveryServiceCallback.getExistingDiscoveryResult(thingUID) == null) {

                    logger.info("{}: Creating ZigBee device {} with bridge {}", node.getIeeeAddress(), thingTypeUID,
                            bridgeUID);

                    Map<String, Object> objProperties = new HashMap<String, Object>();
                    objProperties.put(ZigBeeBindingConstants.CONFIGURATION_MACADDRESS,
                            node.getIeeeAddress().toString());
                    DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                            .withThingType(thingTypeUID).withProperties(objProperties).withBridge(bridgeUID)
                            .withLabel(label).build();

                    // thingDiscovered(discoveryResult);
                }

                // Perform the device properties discovery
                // This is designed to allow us to provide the users with more information about what the device is
                ZigBeeNodePropertyDiscoverer propertyDiscoverer = new ZigBeeNodePropertyDiscoverer();
                Map<String, String> properties = propertyDiscoverer.getProperties(coordinatorHandler, node);

                if ((properties.get(ZigBeeBindingConstants.THING_PROPERTY_MANUFACTURER) != null)
                        && (properties.get(ZigBeeBindingConstants.THING_PROPERTY_MODEL) != null)) {
                    label = properties.get(ZigBeeBindingConstants.THING_PROPERTY_MANUFACTURER).trim() + " "
                            + properties.get(ZigBeeBindingConstants.THING_PROPERTY_MODEL).trim() + " ("
                            + node.getIeeeAddress() + ")";
                }

                logger.info("{}: Update ZigBee device {} with bridge {}", node.getIeeeAddress(), thingTypeUID,
                        bridgeUID);

                Map<String, Object> objProperties = new HashMap<String, Object>(properties);
                objProperties.put(ZigBeeBindingConstants.CONFIGURATION_MACADDRESS, node.getIeeeAddress().toString());
                DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                        .withProperties(objProperties).withBridge(bridgeUID).withLabel(label).build();

                thingDiscovered(discoveryResult);
            }
        };

        scheduler.schedule(pollingRunnable, 10, TimeUnit.MILLISECONDS);
    }
}
