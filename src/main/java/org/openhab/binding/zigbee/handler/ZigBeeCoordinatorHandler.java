/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;

import java.math.BigDecimal;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.discovery.ZigBeeDiscoveryService;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeAddress;
import com.zsmartsystems.zigbee.ZigBeeDevice;
import com.zsmartsystems.zigbee.ZigBeeDeviceAddress;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNetworkStateListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeTransportState;
import com.zsmartsystems.zigbee.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.serialization.ZigBeeDeserializer;
import com.zsmartsystems.zigbee.serialization.ZigBeeSerializer;
import com.zsmartsystems.zigbee.zcl.ZclCluster;

/**
 * The {@link ZigBeeCoordinatorHandler} is responsible for handling commands,
 * which are sent to one of the channels.
 *
 * This is the base coordinator handler. It handles the majority of the interaction
 * with the ZigBeeNetworkManager.
 *
 * The interface coordinators are responsible for opening a ZigBeeTransport implementation
 * and passing this to the {@link ZigBeeCoordinatorHandler}.
 *
 * @author Chris Jackson - Initial contribution
 */
public abstract class ZigBeeCoordinatorHandler extends BaseBridgeHandler
        implements ZigBeeNetworkStateListener, ZigBeeNetworkNodeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeCoordinatorHandler.class);

    protected int panId;
    protected int channelId;
    protected long extendedPanId;

    private ZigBeeTransportTransmit zigbeeTransport;
    private ZigBeeNetworkManager networkManager;

    private Class<?> serializerClass;
    private Class<?> deserializerClass;

    protected byte[] networkKey;

    private ScheduledFuture<?> restartJob = null;

    private ZigBeeDiscoveryService discoveryService;
    private ServiceRegistration discoveryRegistration;

    public ZigBeeCoordinatorHandler(Bridge coordinator) {
        super(coordinator);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee network [{}].", thing.getUID());

        try {
            panId = ((BigDecimal) getConfig().get(PARAMETER_PANID)).intValue();
            channelId = ((BigDecimal) getConfig().get(PARAMETER_CHANNEL)).intValue();
        } catch (ClassCastException e) {

        }

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                ZigBeeBindingConstants.getI18nConstant(ZigBeeBindingConstants.OFFLINE_NOT_INITIALIZED));
    }

    // private void serializeNode(ZigBeeDevice device) {
    // ZigBeeNodeSerializer serializer = new ZigBeeNodeSerializer();

    // serializer.serializeNode(zigbeeApi, device);
    // }

    // public void deserializeNode(String address) {
    // ZigBeeNodeSerializer serializer = new ZigBeeNodeSerializer();

    // List<ZigBeeDevice> endpoints = serializer.deserializeNode(address);
    // if (endpoints == null) {
    // return;
    // }

    // for (final ZigBeeEndpoint endpoint : endpoints) {
    // Check if the node is existing
    // ZigBeeNodeImpl existingNode = zigbeeApi.getZigBeeNetwork().getNode(endpoint.getNode().getIeeeAddress());
    // if (existingNode == null) {
    // zigbeeApi.getZigBeeNetwork().addNode((ZigBeeNodeImpl) endpoint.getNode());
    // } else {
    // ((ZigBeeEndpointImpl) endpoint).setNode(existingNode);
    // }

    // Check if the endpoint is existing

    // ((ZigBeeEndpointImpl) endpoint).setNetworkManager(zigbeeApi.getZigBeeNetworkManager());
    // zigbeeApi.getZigBeeNetwork().addEndpoint(endpoint);
    // }
    // }

    @Override
    public void dispose() {
        // Remove the discovery service
        discoveryService.deactivate();
        discoveryRegistration.unregister();

        // If we have scheduled tasks, stop them
        if (restartJob != null) {
            restartJob.cancel(true);
        }

        // Shut down the ZigBee library
        if (networkManager != null) {
            networkManager.shutdown();
        }
        logger.debug("ZigBee network [{}] closed.", thing.getUID());
    }

    @Override
    public void thingUpdated(Thing thing) {
        super.thingUpdated(thing);
        logger.debug("Updating coordinator [{}]", thing.getUID());
    }

    /**
     * Common initialisation point for all ZigBee coordinators.
     * Called by bridge implementations after they have initialised their interfaces.
     *
     * @param zigbeeTransport a {@link ZigBeeTransportTransmit} interface instance
     * @param serializerClass a {@link ZigBeeSerializer} Class
     * @param deserializerClass a {@link ZigBeeDeserializer} Class
     */
    protected void startZigBee(ZigBeeTransportTransmit zigbeeTransport, Class<?> serializerClass,
            Class<?> deserializerClass) {

        this.zigbeeTransport = zigbeeTransport;
        this.serializerClass = serializerClass;
        this.deserializerClass = deserializerClass;

        // Start the network. This is a scheduled task to ensure we give the coordinator
        // some time to initialise itself!
        startZigBeeNetwork();
    }

    /**
     * Initialise the ZigBee network
     */
    private void initialiseZigBee() {
        logger.debug("Initialising ZigBee coordinator");

        // Start the discovery service
        discoveryService = new ZigBeeDiscoveryService(this);
        discoveryService.activate();

        // And register it as an OSGi service
        discoveryRegistration = bundleContext.registerService(DiscoveryService.class.getName(), discoveryService,
                new Hashtable<String, Object>());

        networkManager = new ZigBeeNetworkManager(zigbeeTransport);
        networkManager.setSerializer(serializerClass, deserializerClass);
        networkManager.addNetworkStateListener(this);
        networkManager.addNetworkNodeListener(this);

        if (!networkManager.initialize()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    ZigBeeBindingConstants.getI18nConstant(ZigBeeBindingConstants.OFFLINE_INITIALIZE_FAIL));
            return;
        }

        int currentChannel = networkManager.getZigBeeChannel();
        int currentPanId = networkManager.getZigBeePanId();
        long currentExtendedPanId = networkManager.getZigBeeExtendedPanId();

        if (channelId != -1 && currentChannel != channelId) {
            // networkManager.setZigBeeChannel(channelId);
        }

        if (panId != 0xffff) {
            // networkManager.setZigBeePanId(panId);
        }

        if (extendedPanId != currentExtendedPanId) {
            // networkManager.setZigBeeExtendedPanId(extendedPanId);
        }

        // Call startup. The setting of the bring to ONLINE will be done via the state listener.
        if (!networkManager.startup()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    ZigBeeBindingConstants.getI18nConstant(ZigBeeBindingConstants.OFFLINE_STARTUP_FAIL));
            return;
        }
    }

    /**
     * If the network initialisation fails, then periodically reschedule a restart
     */
    private void startZigBeeNetwork() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                logger.debug("ZigBee network starting");
                restartJob = null;
                initialiseZigBee();
            }
        };

        logger.debug("Scheduling ZigBee start");
        restartJob = scheduler.schedule(runnable, 1, TimeUnit.SECONDS);
    }

    private ZigBeeDevice getDeviceByIndexOrEndpointId(ZigBeeDeviceAddress deviceAddress) {
        ZigBeeDevice device;
        device = networkManager.getDevice(deviceAddress);
        if (device == null) {
            logger.debug("Error finding ZigBee device with address {}", deviceAddress);
        }
        return device;
    }

    /**
     * Returns a list of all known devices
     *
     * @return list of devices
     */
    public List<ZigBeeDevice> getDeviceList() {
        return networkManager.getDevices();
    }

    public void startDeviceDiscovery() {
        final List<ZigBeeDevice> devices = networkManager.getDevices();
        for (ZigBeeDevice device : devices) {
            // addNewNode(node);
        }

        // TODO: Move to discovery handler
        // Allow devices to join for 60 seconds
        networkManager.permitJoin(true);

        // ZigBeeDiscoveryManager discoveryManager = zigbeeApi.getZigBeeDiscoveryManager();
        // discoveryManager.
    }

    /**
     * Adds a {@link ZigBeeNetworkNodeListener} to receive updates on node status
     *
     * @param listener
     */
    public void addNetworkNodeListener(ZigBeeNetworkNodeListener listener) {
        networkManager.addNetworkNodeListener(listener);
    }

    /**
     * Removes a {@link ZigBeeNetworkNodeListener} to receive updates on node status
     *
     * @param listener
     */
    public void removeNetworkNodeListener(ZigBeeNetworkNodeListener listener) {
        networkManager.removeNetworkNodeListener(listener);
    }

    @Override
    public void nodeAdded(ZigBeeNode node) {
        // logger.debug("ZigBee Node discovered: {}", node);
        // if (node.getLogicalType() == LogicalType.COORDINATOR) {
        // Ignore the coordinator
        // return;
        // }

        // if (discoveryService == null) {
        // logger.error("ZigBee Node discovered but there is no discovery service");
        // return;
        // }
        // discoveryService.nodeDiscovered(node);
    }

    @Override
    public void nodeRemoved(ZigBeeNode node) {
        // TODO Auto-generated method stub
    }

    @Override
    public void nodeUpdated(ZigBeeNode node) {
        // TODO Auto-generated method stub
    }

    public void bind(String address, int cluster) {
        // ZigBeeDevice device = getDevice(address);

        // try {
        // zigbeeApi.bind(device, destination, cluster);
        // } catch (final ZigBeeApiException e) {
        // e.printStackTrace();
        // }
    }

    public ZigBeeDevice getDevice(ZigBeeAddress address) {
        return networkManager.getDevice(address);
    }

    // public ZigBeeNode getNode(int nodeAddress) {
    // return networkManager.getNode(nodeAddress);
    // }

    public Set<ZigBeeDevice> getNodeDevices(IeeeAddress nodeIeeeAddress) {
        return networkManager.getNodeDevices(nodeIeeeAddress);
    }

    public Set<ZigBeeNode> getNodes() {
        return networkManager.getNodes();
    }

    public ZclCluster getCluster(ZigBeeDeviceAddress address, int clusterId) {
        ZigBeeDevice device = networkManager.getDevice(address);
        if (device == null) {
            return null;
        }
        return device.getCluster(clusterId);
    }

    @Override
    public void networkStateUpdated(final ZigBeeTransportState state) {
        switch (state) {
            case UNINITIALISED:
                break;
            case INITIALISING:
                break;
            case ONLINE:
                updateStatus(ThingStatus.ONLINE);
                break;
            case OFFLINE:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                        ZigBeeBindingConstants.getI18nConstant(ZigBeeBindingConstants.OFFLINE_STARTUP_FAIL));
                break;
            default:
                break;
        }
    }

    public ZigBeeNode getNode(IeeeAddress nodeIeeeAddress) {
        return networkManager.getNode(nodeIeeeAddress);
    }

}
