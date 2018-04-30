/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryServiceCallback;
import org.eclipse.smarthome.config.discovery.ExtendedDiscoveryService;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.UID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
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
@Component(immediate = true, service = DiscoveryService.class, configurationPid = "discovery.zigbee")
public class ZigBeeDiscoveryService extends AbstractDiscoveryService implements ExtendedDiscoveryService {
    /**
     * The logger
     */
    private final Logger logger = LoggerFactory.getLogger(ZigBeeDiscoveryService.class);

    /**
     * Default search time
     */
    private final static int SEARCH_TIME = 60;

    private DiscoveryServiceCallback discoveryServiceCallback;

    private final Set<ZigBeeCoordinatorHandler> coordinators = new CopyOnWriteArraySet<>();

    private final Set<ZigBeeDiscoveryParticipant> participants = new CopyOnWriteArraySet<>();
    private final Set<ThingTypeUID> supportedThingTypes = new CopyOnWriteArraySet<>();
    private final Map<UID, ZigBeeNetworkNodeListener> registeredListeners = new ConcurrentHashMap<>();

    public ZigBeeDiscoveryService() {
        super(SEARCH_TIME);
        logger.debug("Starting ZigBeeDiscoveryService");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    protected void addZigBeeDiscoveryParticipant(ZigBeeDiscoveryParticipant participant) {
        participants.add(participant);
        supportedThingTypes.addAll(participant.getSupportedThingTypeUIDs());
    }

    protected void removeZigBeeDiscoveryParticipant(ZigBeeDiscoveryParticipant participant) {
        participants.remove(participant);
        supportedThingTypes.clear();
        for (ZigBeeDiscoveryParticipant currentParticipant : participants) {
            supportedThingTypes.addAll(currentParticipant.getSupportedThingTypeUIDs());
        }
    }

    @Override
    public void deactivate() {
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    protected void addZigBeeCoordinator(ZigBeeCoordinatorHandler coordinator) {
        coordinators.add(coordinator);
        ZigBeeNetworkNodeListener listener = new ZigBeeNetworkNodeListener() {
            @Override
            public void nodeAdded(ZigBeeNode node) {
                ZigBeeDiscoveryService.this.nodeDiscovered(coordinator, node);
            }

            @Override
            public void nodeRemoved(ZigBeeNode node) {
                // Nothing to do
            }

            @Override
            public void nodeUpdated(ZigBeeNode node) {
                // Nothing to do
            }
        };

        coordinator.addNetworkNodeListener(listener);
        registeredListeners.put(coordinator.getUID(), listener);
    }

    protected void removeZigBeeCoordinator(ZigBeeCoordinatorHandler coordinator) {
        coordinators.remove(coordinator);
        coordinator.removeNetworkNodeListener(registeredListeners.remove(coordinator.getUID()));
    }

    @Override
    public void setDiscoveryServiceCallback(DiscoveryServiceCallback discoveryServiceCallback) {
        this.discoveryServiceCallback = discoveryServiceCallback;
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return supportedThingTypes;
    }

    @Override
    public void startScan() {
        for (ZigBeeCoordinatorHandler coordinator : coordinators) {
            if (discoveryServiceCallback != null) {
                for (ZigBeeNode node : coordinator.getNodes()) {
                    if (node.getNetworkAddress() == 0) {
                        continue;
                    }

                    ThingTypeUID thingTypeUID = ZigBeeBindingConstants.THING_TYPE_GENERIC_DEVICE;
                    ThingUID bridgeUID = coordinator.getThing().getUID();

                    String thingId = node.getIeeeAddress().toString().toLowerCase().replaceAll("[^a-z0-9_/]", "");
                    ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, thingId);

                    if (discoveryServiceCallback.getExistingThing(thingUID) != null
                            || discoveryServiceCallback.getExistingDiscoveryResult(thingUID) != null) {
                        continue;
                    }

                    logger.debug("{}: Discovery: Starting discovery for existing device {}", node.getIeeeAddress(),
                            thingUID);
                    nodeDiscovered(coordinator, node);
                }
            }

            logger.debug("Starting ZigBee scan for {}", coordinator.getUID());
            coordinator.scanStart();
        }

    }

    /**
     * Adds a new device to the network.
     * This starts a thread to read information about the device so we can
     * create the thingType, and the label for the user.
     *
     * @param node the new {@link ZigBeeNode}
     */
    private void nodeDiscovered(ZigBeeCoordinatorHandler coordinator, final ZigBeeNode node) {
        // If this is the coordinator (NWK address 0), ignore this device
        if (node.getLogicalType() == LogicalType.COORDINATOR || node.getNetworkAddress() == 0) {
            return;
        }

        ThingTypeUID defaultThingTypeUID = ZigBeeBindingConstants.THING_TYPE_GENERIC_DEVICE;
        ThingUID bridgeUID = coordinator.getUID();

        String defaultThingId = node.getIeeeAddress().toString().toLowerCase().replaceAll("[^a-z0-9_/]", "");
        ThingUID defaultThingUID = new ThingUID(defaultThingTypeUID, bridgeUID, defaultThingId);

        // If this already exists as a thing, then no need to rediscover
        if (discoveryServiceCallback != null && discoveryServiceCallback.getExistingThing(defaultThingUID) != null) {
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
                        && discoveryServiceCallback.getExistingDiscoveryResult(defaultThingUID) == null) {

                    logger.debug("{}: Creating ZigBee device {} with bridge {}", node.getIeeeAddress(),
                            defaultThingTypeUID, bridgeUID);

                    Map<String, Object> objProperties = new HashMap<String, Object>();
                    objProperties.put(ZigBeeBindingConstants.CONFIGURATION_MACADDRESS,
                            node.getIeeeAddress().toString());
                    DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(defaultThingUID)
                            .withThingType(defaultThingTypeUID).withProperties(objProperties).withBridge(bridgeUID)
                            .withLabel(label).build();

                    thingDiscovered(discoveryResult);
                }

                if (!node.isDiscovered()) {
                    logger.debug("{}: Node discovery not complete", node.getIeeeAddress());
                    return;
                }

                // Perform the device properties discovery
                // This is designed to allow us to provide the users with more information about what the device is.
                // This information is also performed here so that it is available to discovery participants
                // as this can take some time and discovery participants should return promptly.
                ZigBeeNodePropertyDiscoverer propertyDiscoverer = new ZigBeeNodePropertyDiscoverer();
                Map<String, String> properties = propertyDiscoverer.getProperties(node);

                // If we know the manufacturer and model, then give this device a name and a thing type
                if ((properties.get(Thing.PROPERTY_VENDOR) != null)
                        && (properties.get(Thing.PROPERTY_MODEL_ID) != null)) {
                    label = properties.get(Thing.PROPERTY_VENDOR) + " " + properties.get(Thing.PROPERTY_MODEL_ID);
                }
                Map<String, Object> objProperties = new HashMap<String, Object>(properties);
                objProperties.put(ZigBeeBindingConstants.CONFIGURATION_MACADDRESS, node.getIeeeAddress().toString());

                boolean discoveryAdded = false;
                for (ZigBeeDiscoveryParticipant participant : participants) {
                    try {
                        DiscoveryResult discoveryResult = participant.createResult(bridgeUID, node, objProperties);
                        if (discoveryResult != null) {
                            discoveryAdded = true;
                            thingDiscovered(discoveryResult);
                        }
                    } catch (Exception e) {
                        logger.error("Participant '{}' threw an exception", participant.getClass().getName(), e);
                    }
                }

                if (discoveryAdded) {
                    thingRemoved(defaultThingUID);
                } else {
                    logger.debug("{}: Update ZigBee device {} with bridge {}, label '{}'", node.getIeeeAddress(),
                            defaultThingTypeUID, bridgeUID, label);

                    DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(defaultThingUID)
                            .withThingType(defaultThingTypeUID).withProperties(objProperties).withBridge(bridgeUID)
                            .withLabel(label).build();

                    thingDiscovered(discoveryResult);
                }
                coordinator.serializeNetwork();
            }
        };

        scheduler.schedule(pollingRunnable, 10, TimeUnit.MILLISECONDS);
    }
}
