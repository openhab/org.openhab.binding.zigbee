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
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.discovery.ZigBeeDiscoveryService;
import org.openhab.binding.zigbee.internal.ZigBeeActivator;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ExtendedPanId;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress;
import com.zsmartsystems.zigbee.ZigBeeKey;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager.ZigBeeInitializeResponse;
import com.zsmartsystems.zigbee.ZigBeeNetworkMeshMonitor;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNetworkStateListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.serialization.ZigBeeDeserializer;
import com.zsmartsystems.zigbee.serialization.ZigBeeSerializer;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareUpdate;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportState;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zdo.field.NeighborTable;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor.LogicalType;
import com.zsmartsystems.zigbee.zdo.field.RoutingTable;

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
    protected ExtendedPanId extendedPanId;

    private IeeeAddress nodeIeeeAddress = null;

    protected ZigBeeTransportTransmit zigbeeTransport;
    private ZigBeeNetworkManager networkManager;

    private Class<?> serializerClass;
    private Class<?> deserializerClass;

    protected ZigBeeKey networkKey;

    private boolean macAddressSet = false;

    /**
     * Set to true on startup if we want to reinitialize the network
     */
    private boolean initializeNetwork = false;

    private ScheduledFuture<?> restartJob = null;

    private ZigBeeNetworkMeshMonitor meshMonitor;
    private ZigBeeDiscoveryService discoveryService;
    private ServiceRegistration discoveryRegistration;

    private final TranslationProvider translationProvider;

    public ZigBeeCoordinatorHandler(Bridge coordinator, TranslationProvider translationProvider) {
        super(coordinator);

        this.translationProvider = translationProvider;
    }

    private String getI18nConstant(String constant, Object... arguments) {
        TranslationProvider translationProviderLocal = translationProvider;
        if (translationProviderLocal == null) {
            return MessageFormat.format(constant, arguments);
        }
        return translationProviderLocal.getText(ZigBeeActivator.getContext().getBundle(), constant, constant, null,
                arguments);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee network [{}].", thing.getUID());
        panId = 0xffff;
        channelId = 0;
        initializeNetwork = false;
        String networkKeyString = "";

        try {
            if (getConfig().get(CONFIGURATION_CHANNEL) != null) {
                logger.debug("Channel {}", getConfig().get(CONFIGURATION_CHANNEL));
                channelId = ((BigDecimal) getConfig().get(CONFIGURATION_CHANNEL)).intValue();
            }

            if (getConfig().get(CONFIGURATION_PANID) != null) {
                logger.debug("PANID {}", getConfig().get(CONFIGURATION_PANID));
                panId = ((BigDecimal) getConfig().get(CONFIGURATION_PANID)).intValue();
            }

            if (getConfig().get(CONFIGURATION_EXTENDEDPANID) != null) {
                logger.debug("EPANID {}", getConfig().get(CONFIGURATION_EXTENDEDPANID));
                extendedPanId = new ExtendedPanId((String) getConfig().get(CONFIGURATION_EXTENDEDPANID));
            }

            Object param = getConfig().get(CONFIGURATION_NETWORKKEY);
            logger.debug("Key {}", getConfig().get(CONFIGURATION_NETWORKKEY));
            if (param != null && param instanceof String) {
                networkKeyString = (String) param;
            }

        } catch (ClassCastException | NumberFormatException e) {
            logger.debug("Initializing ZigBee network [{}].", thing.getUID(), e.getMessage());
            logger.debug("ZigBee initialisation exception", e);
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        if (getConfig().get(CONFIGURATION_INITIALIZE) != null) {
            initializeNetwork = (Boolean) getConfig().get(CONFIGURATION_INITIALIZE);
        } else {
            initializeNetwork = true;
        }

        if (extendedPanId.equals(new ExtendedPanId()) || panId == 0) {
            initializeNetwork = true;
        }

        if (initializeNetwork) {
            logger.debug("Initialising network");

            if (channelId == 0) {
                channelId = 11;
                logger.debug("Channel set to 11.");

                try {
                    // Update the channel
                    Configuration configuration = editConfiguration();
                    configuration.put(CONFIGURATION_CHANNEL, channelId);
                    updateConfiguration(configuration);
                } catch (IllegalStateException e) {
                    logger.debug("Error updating configuration: Unable to set channel.", e);
                }
            }

            if (panId == 0) {
                panId = (int) Math.floor((Math.random() * 65534));
                logger.debug("Created random ZigBee PAN ID [{}].", String.format("%04X", panId));

                try {
                    // Update the PAN ID
                    Configuration configuration = editConfiguration();
                    configuration.put(CONFIGURATION_PANID, panId);
                    updateConfiguration(configuration);
                } catch (IllegalStateException e) {
                    logger.debug("Error updating configuration: Unable to set PanID.", e);
                }
            }

            if (!extendedPanId.isValid()) {
                int[] pan = new int[8];
                for (int cnt = 0; cnt < 8; cnt++) {
                    pan[cnt] = (int) Math.floor((Math.random() * 255));
                }
                extendedPanId = new ExtendedPanId(pan);
                logger.debug("Created random ZigBee extended PAN ID [{}].", extendedPanId);

                try {
                    // Update the Extended PAN ID
                    Configuration configuration = editConfiguration();
                    configuration.put(CONFIGURATION_EXTENDEDPANID, extendedPanId.toString());
                    updateConfiguration(configuration);
                } catch (IllegalStateException e) {
                    logger.debug("Error updating configuration: Unable to set Extended PanID.", e);
                }
            }

            try {
                // Reset the initialization flag
                Configuration configuration = editConfiguration();
                configuration.put(CONFIGURATION_INITIALIZE, false);
                updateConfiguration(configuration);
            } catch (IllegalStateException e) {
                logger.debug("Error updating configuration: Unable to reset initialize flag.", e);
            }
        }

        logger.debug("Key String {}", networkKeyString);

        // Process the network key
        try {
            networkKey = new ZigBeeKey(networkKeyString);
        } catch (IllegalArgumentException e) {
            networkKey = new ZigBeeKey();
        }

        // If no key exists, generate a random key and save it back to the configuration
        if (!networkKey.isValid()) {
            networkKey = ZigBeeKey.createRandom();
            logger.debug("Key initialised {}", networkKey);
        }

        logger.debug("Key final array {}", networkKey);
    }

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

        // ZigBeeNetworkStateSerializer networkStateSerializer = new ZigBeeNetworkStateSerializerImpl();

        // Configure the network manager
        networkManager = new ZigBeeNetworkManager(zigbeeTransport);
        // networkManager.setNetworkStateSerializer(networkStateSerializer);
        networkManager.setSerializer(serializerClass, deserializerClass);
        networkManager.addNetworkStateListener(this);
        networkManager.addNetworkNodeListener(this);

        logger.debug("Key initialise {}", networkKey);

        // Initialise the network
        ZigBeeInitializeResponse initResponse = networkManager.initialize();
        if (initResponse == ZigBeeInitializeResponse.FAILED) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE,
                    getI18nConstant(ZigBeeBindingConstants.OFFLINE_INITIALIZE_FAIL));
            return;
        }

        logger.debug("initResponse is {}", initResponse);

        // If we're not joined, then join
        if (initResponse == ZigBeeInitializeResponse.NOT_JOINED) {
            initializeNetwork = true;
        }

        logger.debug("initializeNetwork is {}", initializeNetwork);

        // Get the initial network configuration
        int currentChannel = networkManager.getZigBeeChannel();
        int currentPanId = networkManager.getZigBeePanId();
        ExtendedPanId currentExtendedPanId = networkManager.getZigBeeExtendedPanId();

        if (initializeNetwork) {
            networkManager.setZigBeeNetworkKey(networkKey);
            networkManager.setZigBeeChannel(channelId);
            networkManager.setZigBeePanId(panId);
            networkManager.setZigBeeExtendedPanId(extendedPanId);
        }

        // Call startup. The setting of the bring to ONLINE will be done via the state listener.
        if (!networkManager.startup(initializeNetwork)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE,
                    getI18nConstant(ZigBeeBindingConstants.OFFLINE_STARTUP_FAIL));
            return;
        }

        // Get the final network configuration
        currentChannel = networkManager.getZigBeeChannel();
        currentPanId = networkManager.getZigBeePanId();
        currentExtendedPanId = networkManager.getZigBeeExtendedPanId();
        logger.debug("Initialise done....... {}  {}  {}", currentChannel, currentPanId, currentExtendedPanId);

        try {
            // Persist the network configuration
            Configuration configuration = editConfiguration();
            configuration.put(CONFIGURATION_PANID, currentPanId);
            configuration.put(CONFIGURATION_EXTENDEDPANID, currentExtendedPanId.toString());
            configuration.put(CONFIGURATION_CHANNEL, currentChannel);

            // If the thing is defined statically, then this will fail and we will never start!
            updateConfiguration(configuration);
        } catch (IllegalStateException e) {
            logger.debug("Error updating configuration", e);
        }

        // Start the mesh monitor
        meshMonitor = new ZigBeeNetworkMeshMonitor(networkManager);
        meshMonitor.startup(90);
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

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("{}: Configuration received (Coordinator).", nodeIeeeAddress);

        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            switch (configurationParameter.getKey()) {
                case ZigBeeBindingConstants.CONFIGURATION_JOINENABLE:
                    if ((Boolean) configurationParameter.getValue() == true) {
                        permitJoin(nodeIeeeAddress, 60);
                    }
                    configuration.put(CONFIGURATION_JOINENABLE, false);
                    break;
                default:
                    configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
                    logger.debug("{}: Unhandled configuration parameter {} >> {}.", nodeIeeeAddress,
                            configurationParameter.getKey(), configurationParameter.getValue());
                    break;
            }
        }

        // Persist changes
        updateConfiguration(configuration);
    }

    public void startDeviceDiscovery() {
        // TODO: Move to discovery handler?
        // Allow devices to join for 60 seconds
        networkManager.permitJoin(60);

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
        logger.debug("ZigBee Node discovered: {}", node);
        if (node.getLogicalType() == LogicalType.COORDINATOR) {
            // Ignore the coordinator
            return;
        }

        if (discoveryService == null) {
            logger.error("ZigBee Node discovered but there is no discovery service");
            return;
        }
        discoveryService.nodeDiscovered(node);
    }

    @Override
    public void nodeRemoved(ZigBeeNode node) {
        // Nothing to do here...
    }

    @Override
    public void nodeUpdated(ZigBeeNode node) {
        // We're only interested in the coordinator here.
        if (node.getNetworkAddress() != 0) {
            return;
        }

        if (!macAddressSet) {
            macAddressSet = true;
            try {
                nodeIeeeAddress = node.getIeeeAddress();

                // Reset the initialization flag
                Configuration configuration = editConfiguration();
                configuration.put(CONFIGURATION_MACADDRESS, node.getIeeeAddress().toString());
                updateConfiguration(configuration);
            } catch (IllegalStateException e) {
                logger.debug("Error updating configuration: Unable to set mac address.", e);
            }
        }

        Map<String, String> properties = editProperties();

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[");
        boolean first = true;
        for (NeighborTable neighbor : node.getNeighbors()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            first = false;

            Map<String, Object> object = new HashMap<String, Object>();
            object.put("address", neighbor.getNetworkAddress());
            object.put("macaddress", neighbor.getExtendedAddress());
            object.put("depth", neighbor.getDepth());
            object.put("lqi", neighbor.getLqi());
            object.put("joining", neighbor.getPermitJoining());
            jsonBuilder.append(ZigBeeBindingConstants.propertiesToJson(object));
        }
        jsonBuilder.append("]");
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_NEIGHBORS, jsonBuilder.toString());

        jsonBuilder = new StringBuilder();
        jsonBuilder.append("[");
        first = true;
        for (RoutingTable route : node.getRoutes()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            first = false;

            Map<String, Object> object = new HashMap<String, Object>();
            object.put("destination", route.getDestinationAddress());
            object.put("next_hop", route.getNextHopAddress());
            object.put("state", route.getStatus());
            jsonBuilder.append(ZigBeeBindingConstants.propertiesToJson(object));
        }
        jsonBuilder.append("]");
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_ROUTES, jsonBuilder.toString());

        properties.put(ZigBeeBindingConstants.THING_PROPERTY_ASSOCIATEDDEVICES, node.getAssociatedDevices().toString());
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_PERMITJOINING, Boolean.toString(node.isJoiningEnabled()));
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_LASTUPDATE,
                ZigBeeBindingConstants.getISO8601StringForDate(node.getLastUpdateTime()));
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_NETWORKADDRESS, node.getNetworkAddress().toString());

        properties.put(ZigBeeBindingConstants.THING_PROPERTY_LOGICALTYPE, node.getLogicalType().toString());

        // If this dongle supports firmware updates, then set the version
        if (zigbeeTransport instanceof ZigBeeTransportFirmwareUpdate) {
            ZigBeeTransportFirmwareUpdate firmwareTransport = (ZigBeeTransportFirmwareUpdate) zigbeeTransport;
            properties.put(Thing.PROPERTY_FIRMWARE_VERSION, firmwareTransport.getFirmwareVersion());
        }

        updateProperties(properties);
    }

    public ZigBeeEndpoint getEndpoint(ZigBeeEndpointAddress address) {
        if (networkManager == null) {
            return null;
        }
        ZigBeeNode node = networkManager.getNode(address.getAddress());
        if (node == null) {
            return null;
        }
        return node.getEndpoint(address.getEndpoint());
    }

    public Collection<ZigBeeEndpoint> getNodeEndpoints(IeeeAddress nodeIeeeAddress) {
        if (networkManager == null) {
            return Collections.<ZigBeeEndpoint> emptySet();
        }
        ZigBeeNode node = networkManager.getNode(nodeIeeeAddress);
        if (node == null) {
            return Collections.<ZigBeeEndpoint> emptySet();
        }

        return node.getEndpoints();
    }

    public Set<ZigBeeNode> getNodes() {
        return networkManager.getNodes();
    }

    public ZclCluster getCluster(ZigBeeEndpointAddress address, int clusterId) {
        ZigBeeEndpoint endpoint = getEndpoint(address);
        if (endpoint == null) {
            return null;
        }
        return endpoint.getCluster(clusterId);
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
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE,
                        getI18nConstant(ZigBeeBindingConstants.OFFLINE_STARTUP_FAIL));
                break;
            default:
                break;
        }
    }

    public ZigBeeNode getNode(IeeeAddress nodeIeeeAddress) {
        if (networkManager == null) {
            return null;
        }
        return networkManager.getNode(nodeIeeeAddress);
    }

    /**
     * Permit joining only for the specified node
     *
     * @param address the 16 bit network address of the node to enable joining
     * @param duration the duration of the join
     */
    public boolean permitJoin(IeeeAddress address, int duration) {
        logger.debug("{}: ZigBee join command", address);
        ZigBeeNode node = networkManager.getNode(address);
        if (node == null) {
            logger.debug("{}: ZigBee join command - node not found", address);
            return false;
        }

        logger.debug("{}: ZigBee join command to {}", address, node.getNetworkAddress());

        networkManager.permitJoin(new ZigBeeEndpointAddress(node.getNetworkAddress()), duration);
        return true;
    }

    /**
     * Sends a ZDO Leave Request to a device requesting that an end device leave the network.
     * <p>
     * This method will send the ZDO message to the device itself requesting it leave the network
     *
     * @param address the network address to leave
     * @return true if the command is sent
     */
    public boolean leave(IeeeAddress address) {
        // First we want to make sure that join is disabled
        networkManager.permitJoin(0);

        logger.debug("{}: ZigBee leave command", address);
        ZigBeeNode node = networkManager.getNode(address);
        if (node == null) {
            logger.debug("{}: ZigBee leave command - node not found", address);
            return false;
        }

        logger.debug("{}: ZigBee leave command to {}", address, node.getNetworkAddress());

        networkManager.leave(node.getNetworkAddress(), node.getIeeeAddress());
        // networkManager.leave(ZigBeeBroadcastDestination.BROADCAST_ALL_DEVICES.getKey(), node.getIeeeAddress());
        return true;
    }

    public IeeeAddress getIeeeAddress() {
        return nodeIeeeAddress;
    }

    /**
     * Search for a node - will perform a discovery on the defined {@link IeeeAddress}
     * 
     * @param nodeIeeeAddress {@link IeeeAddress} of the node to discover
     */
    public void rediscoverNode(IeeeAddress nodeIeeeAddress) {
        if (networkManager == null) {
            return;
        }
        networkManager.rediscoverNode(nodeIeeeAddress);
    }
}
