/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
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
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor.LogicalType;

/**
 * The {@link ZigBeeDiscoveryService} tracks ZigBee devices which are associated
 * to coordinator.
 *
 * @author Chris Jackson - Initial contribution
 *
 */
public class ZigBeeDiscoveryService extends AbstractDiscoveryService
        implements ExtendedDiscoveryService, ZigBeeNetworkNodeListener {
    /**
     * The logger
     */
    private final Logger logger = LoggerFactory.getLogger(ZigBeeDiscoveryService.class);

    /**
     * Default search time
     */
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

        coordinatorHandler.addNetworkNodeListener(this);
    }

    @Override
    public void deactivate() {
        logger.debug("Deactivating ZigBee discovery service for {}", coordinatorHandler.getThing().getUID());

        coordinatorHandler.removeNetworkNodeListener(this);
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
    public void startScan() {
        if (coordinatorHandler.getThing().getStatus() != ThingStatus.ONLINE) {
            logger.debug("ZigBee coordinator is offline - aborted scan for {}", coordinatorHandler.getThing().getUID());
            return;
        }

        if (discoveryServiceCallback != null) {
            for (ZigBeeNode node : coordinatorHandler.getNodes()) {
                if (node.getNetworkAddress() == 0) {
                    continue;
                }

                ThingTypeUID thingTypeUID = ZigBeeBindingConstants.THING_TYPE_GENERIC_DEVICE;
                ThingUID bridgeUID = coordinatorHandler.getThing().getUID();

                String thingId = node.getIeeeAddress().toString().toLowerCase().replaceAll("[^a-z0-9_/]", "");
                ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, thingId);

                if (discoveryServiceCallback.getExistingThing(thingUID) != null
                        || discoveryServiceCallback.getExistingDiscoveryResult(thingUID) != null) {
                    continue;
                }

                logger.debug("{}: Discovery: Starting discovery for existing device {}", node.getIeeeAddress(),
                        thingUID);
                nodeDiscovered(node);
            }
        }

        logger.debug("Starting ZigBee scan for {}", coordinatorHandler.getThing().getUID());

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
    private void nodeDiscovered(final ZigBeeNode node) {
        // If this is the coordinator (NWK address 0), ignore this device
        if (node.getLogicalType() == LogicalType.COORDINATOR || node.getNetworkAddress() == 0) {
            return;
        }

        ThingTypeUID thingTypeUID = ZigBeeBindingConstants.THING_TYPE_GENERIC_DEVICE;
        ThingUID bridgeUID = coordinatorHandler.getThing().getUID();

        String thingId = node.getIeeeAddress().toString().toLowerCase().replaceAll("[^a-z0-9_/]", "");
        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, thingId);

        // If this already exists as a thing, then no need to rediscover
        if (discoveryServiceCallback != null && discoveryServiceCallback.getExistingThing(thingUID) != null) {
            return;
        }

        Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                logger.info("{}: Starting ZigBee device discovery", node.getIeeeAddress());

                // Set the default label
                String label = "Unknown ZigBee Device " + node.getIeeeAddress();

                // Add this device to the inbox if we don't already know about it
                // Do this here incase the device doesn't respond to the discovery messages.
                // This keeps the user informed.
                if (discoveryServiceCallback != null
                        && discoveryServiceCallback.getExistingDiscoveryResult(thingUID) == null) {

                    logger.debug("{}: Creating ZigBee device {} with bridge {}", node.getIeeeAddress(), thingTypeUID,
                            bridgeUID);

                    Map<String, Object> objProperties = new HashMap<String, Object>();
                    objProperties.put(ZigBeeBindingConstants.CONFIGURATION_MACADDRESS,
                            node.getIeeeAddress().toString());
                    DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                            .withThingType(thingTypeUID).withProperties(objProperties).withBridge(bridgeUID)
                            .withLabel(label).build();

                    thingDiscovered(discoveryResult);
                }

                if (!node.isDiscovered()) {
                    logger.debug("{}: Node discovery not complete", node.getIeeeAddress());
                    return;
                }

                // Perform the device properties discovery
                // This is designed to allow us to provide the users with more information about what the device is
                ZigBeeNodePropertyDiscoverer propertyDiscoverer = new ZigBeeNodePropertyDiscoverer();
                Map<String, String> properties = propertyDiscoverer.getProperties(coordinatorHandler, node);

                // If we know the manufacturer and model, then give this device a name and a thing type
                if ((properties.get(Thing.PROPERTY_VENDOR) != null)
                        && (properties.get(Thing.PROPERTY_MODEL_ID) != null)) {
                    label = properties.get(Thing.PROPERTY_VENDOR) + " " + properties.get(Thing.PROPERTY_MODEL_ID);
                }

                logger.debug("{}: Update ZigBee device {} with bridge {}, label '{}'", node.getIeeeAddress(),
                        thingTypeUID, bridgeUID, label);

                Map<String, Object> objProperties = new HashMap<String, Object>(properties);
                objProperties.put(ZigBeeBindingConstants.CONFIGURATION_MACADDRESS, node.getIeeeAddress().toString());
                DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                        .withProperties(objProperties).withBridge(bridgeUID).withLabel(label).build();

                thingDiscovered(discoveryResult);

                coordinatorHandler.serializeNetwork();
            }
        };

        scheduler.schedule(pollingRunnable, 10, TimeUnit.MILLISECONDS);
    }

    @Override
    public void nodeAdded(ZigBeeNode node) {
        logger.debug("{}: Discovery notification", node.getIeeeAddress());
        nodeDiscovered(node);
    }

    @Override
    public void nodeUpdated(ZigBeeNode node) {
        // Not needed
    }

    @Override
    public void nodeRemoved(ZigBeeNode node) {
        // Not needed
    }
}
