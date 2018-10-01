/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.common.registry.Identifiable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.internal.ZigBeeNetworkStateSerializerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ExtendedPanId;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeChannel;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeNetworkMeshMonitor;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNetworkStateListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import com.zsmartsystems.zigbee.app.iasclient.ZigBeeIasCieExtension;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaUpgradeExtension;
import com.zsmartsystems.zigbee.security.ZigBeeKey;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.serialization.ZigBeeDeserializer;
import com.zsmartsystems.zigbee.serialization.ZigBeeSerializer;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.TrustCentreJoinMode;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareUpdate;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportState;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zdo.field.NeighborTable;
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
        implements Identifiable<ThingUID>, ZigBeeNetworkStateListener, ZigBeeNetworkNodeListener {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeCoordinatorHandler.class);

    protected int panId;
    protected int channelId;
    protected ExtendedPanId extendedPanId;

    private IeeeAddress nodeIeeeAddress = null;

    protected ZigBeeTransportTransmit zigbeeTransport;
    private ZigBeeNetworkManager networkManager;

    private Class<?> serializerClass;
    private Class<?> deserializerClass;

    private ZigBeeNetworkStateSerializerImpl networkStateSerializer;

    protected ZigBeeKey linkKey;
    protected ZigBeeKey networkKey;

    private TransportConfig transportConfig;

    private final Set<ZigBeeNetworkNodeListener> listeners = new HashSet<>();

    private boolean macAddressSet = false;

    private final int MESH_UPDATE_TIME = 300;

    /**
     * Set to true on startup if we want to reinitialize the network
     */
    private boolean initializeNetwork = false;

    private ScheduledFuture<?> restartJob = null;

    private ZigBeeNetworkMeshMonitor meshMonitor = null;

    private volatile boolean bridgeRemoved = false;

    /**
     * Default ZigBeeAlliance09 link key
     */
    private final static ZigBeeKey KEY_ZIGBEE_ALLIANCE_O9 = new ZigBeeKey(new int[] { 0x5A, 0x69, 0x67, 0x42, 0x65,
            0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39 });

    public ZigBeeCoordinatorHandler(Bridge coordinator) {
        super(coordinator);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee network [{}].", thing.getUID());
        panId = 0xffff;
        channelId = 0;
        initializeNetwork = false;
        String linkKeyString = "";
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
            logger.debug("Network Key {}", getConfig().get(CONFIGURATION_NETWORKKEY));
            if (param != null && param instanceof String) {
                networkKeyString = (String) param;
            }

            param = getConfig().get(CONFIGURATION_LINKKEY);
            logger.debug("Link Key {}", getConfig().get(CONFIGURATION_LINKKEY));
            if (param != null && param instanceof String) {
                linkKeyString = (String) param;
            }
        } catch (ClassCastException | NumberFormatException e) {
            logger.error("{}: ZigBee initialisation exception ", thing.getUID(), e);
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        if (getConfig().get(CONFIGURATION_INITIALIZE) != null) {
            initializeNetwork = (Boolean) getConfig().get(CONFIGURATION_INITIALIZE);
        } else {
            initializeNetwork = true;
        }

        if (extendedPanId == null || extendedPanId.equals(new ExtendedPanId()) || panId == 0) {
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
                    logger.error("Error updating configuration: Unable to set channel. ", e);
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
                    logger.error("Error updating configuration: Unable to set PanID. ", e);
                }
            }

            if (extendedPanId != null && !extendedPanId.isValid()) {
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
                    logger.error("Error updating configuration: Unable to set Extended PanID. ", e);
                }
            }

            try {
                // Reset the initialization flag
                Configuration configuration = editConfiguration();
                configuration.put(CONFIGURATION_INITIALIZE, false);
                updateConfiguration(configuration);
            } catch (IllegalStateException e) {
                logger.error("Error updating configuration: Unable to reset initialize flag. ", e);
            }
        }

        // Process the network key
        try {
            logger.debug("Network Key String {}", networkKeyString);
            networkKey = new ZigBeeKey(networkKeyString);
        } catch (IllegalArgumentException e) {
            networkKey = new ZigBeeKey();
        }

        // If no key exists, generate a random key and save it back to the configuration
        if (!networkKey.isValid()) {
            networkKey = ZigBeeKey.createRandom();
            logger.debug("Network key initialised {}", networkKey);
        }

        logger.debug("Network key final array {}", networkKey);

        // Process the link key
        try {
            logger.debug("Link Key String {}", linkKeyString);
            linkKey = new ZigBeeKey(linkKeyString);
        } catch (IllegalArgumentException e) {
            linkKey = KEY_ZIGBEE_ALLIANCE_O9;
            logger.debug("Link Key String has invalid format. Revert to default key.");
        }

        logger.debug("Link key final array {}", linkKey);
    }

    @Override
    public void handleCommand(@NonNull ChannelUID channelUID, @NonNull Command command) {
    }

    /**
     * A dongle specific initialisation method. This can be overridden by coordinator handlers and is called just before
     * the {@link ZigBeeTransportTransmit#startup(boolean)} is called.
     */
    protected void initializeDongleSpecific() {
        // Can be overridden to provide dongle specific configuration
    }

    @Override
    public void dispose() {
        // If we have scheduled tasks, stop them
        if (restartJob != null) {
            restartJob.cancel(true);
        }

        if (meshMonitor != null) {
            meshMonitor.shutdown();
        }

        if (networkManager != null) {
            synchronized (listeners) {
                for (ZigBeeNetworkNodeListener listener : listeners) {
                    networkManager.removeNetworkNodeListener(listener);
                }
            }

            // Shut down the ZigBee library
            networkManager.shutdown();
        }

        if (networkStateSerializer != null && bridgeRemoved) {
            networkStateSerializer.delete();
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
     * @param transportConfig any binding specific configuration that needs to be sent
     */
    protected void startZigBee(ZigBeeTransportTransmit zigbeeTransport, TransportConfig transportConfig) {
        startZigBee(zigbeeTransport, transportConfig, DefaultSerializer.class, DefaultDeserializer.class);
    }

    /**
     * Common initialisation point for all ZigBee coordinators.
     * Called by bridge implementations after they have initialised their interfaces.
     *
     * @param zigbeeTransport a {@link ZigBeeTransportTransmit} interface instance
     * @param transportConfig any binding specific configuration that needs to be sent
     * @param serializerClass a {@link ZigBeeSerializer} Class
     * @param deserializerClass a {@link ZigBeeDeserializer} Class
     */
    protected void startZigBee(ZigBeeTransportTransmit zigbeeTransport, TransportConfig transportConfig,
            Class<?> serializerClass, Class<?> deserializerClass) {
        updateStatus(ThingStatus.UNKNOWN);

        this.zigbeeTransport = zigbeeTransport;
        this.transportConfig = transportConfig;
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

        String networkId = getThing().getUID().toString().replaceAll(":", "_");
        networkStateSerializer = new ZigBeeNetworkStateSerializerImpl(networkId);

        // Configure the network manager
        networkManager = new ZigBeeNetworkManager(zigbeeTransport);
        networkManager.setNetworkStateSerializer(networkStateSerializer);
        networkManager.setSerializer(serializerClass, deserializerClass);
        networkManager.addNetworkStateListener(this);
        networkManager.addNetworkNodeListener(this);

        // Add the extensions to the network
        networkManager.addExtension(new ZigBeeIasCieExtension());
        networkManager.addExtension(new ZigBeeOtaUpgradeExtension());

        // Add any listeners that were registered before the manager was registered
        synchronized (listeners) {
            for (ZigBeeNetworkNodeListener listener : listeners) {
                networkManager.addNetworkNodeListener(listener);
            }
        }

        // Initialise the network
        switch (networkManager.initialize()) {
            case SUCCESS:
                break;
            case BAD_RESPONSE:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, ZigBeeBindingConstants.OFFLINE_BAD_RESPONSE);
                return;
            case COMMUNICATION_ERROR:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, ZigBeeBindingConstants.OFFLINE_COMMS_FAIL);
                return;
            default:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE,
                        ZigBeeBindingConstants.OFFLINE_INITIALIZE_FAIL);
                return;
        }

        // Get the initial network configuration
        ZigBeeChannel currentChannel = networkManager.getZigBeeChannel();
        int currentPanId = networkManager.getZigBeePanId();
        ExtendedPanId currentExtendedPanId = networkManager.getZigBeeExtendedPanId();

        if (initializeNetwork) {
            logger.debug("Link key initialise {}", linkKey);
            logger.debug("Network key initialise {}", networkKey);
            networkManager.setZigBeeLinkKey(linkKey);
            networkManager.setZigBeeNetworkKey(networkKey);
            networkManager.setZigBeeChannel(ZigBeeChannel.create(channelId));
            networkManager.setZigBeePanId(panId);
            networkManager.setZigBeeExtendedPanId(extendedPanId);
        }

        if (getConfig().get(CONFIGURATION_TRUSTCENTREMODE) != null) {
            String mode = (String) getConfig().get(CONFIGURATION_TRUSTCENTREMODE);
            TrustCentreJoinMode linkMode = TrustCentreJoinMode.valueOf(mode);
            transportConfig.addOption(TransportConfigOption.TRUST_CENTRE_JOIN_MODE, linkMode);
        }

        zigbeeTransport.updateTransportConfig(transportConfig);

        // Call startup. The setting of the bring to ONLINE will be done via the state listener.
        if (networkManager.startup(initializeNetwork) != ZigBeeStatus.SUCCESS) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, ZigBeeBindingConstants.OFFLINE_STARTUP_FAIL);
            return;
        }

        // Get the final network configuration
        currentChannel = networkManager.getZigBeeChannel();
        currentPanId = networkManager.getZigBeePanId();
        currentExtendedPanId = networkManager.getZigBeeExtendedPanId();
        logger.debug("ZigBee Initialise done. channel={}, PanId={}  EPanId={}", currentChannel, currentPanId,
                currentExtendedPanId);

        try {
            // Persist the network configuration
            Configuration configuration = editConfiguration();
            configuration.put(CONFIGURATION_PANID, currentPanId);
            if (currentExtendedPanId != null) {
                configuration.put(CONFIGURATION_EXTENDEDPANID, currentExtendedPanId.toString());
            }
            configuration.put(CONFIGURATION_CHANNEL, currentChannel.getChannel());

            // If the thing is defined statically, then this will fail and we will never start!
            updateConfiguration(configuration);
        } catch (IllegalStateException e) {
            logger.error("Error updating configuration ", e);
        }

        initializeDongleSpecific();

        // Add the IAS Zone cluster to the network manager so we respond to the MatchDescriptor
        networkManager.addSupportedCluster(ZclIasZoneCluster.CLUSTER_ID);

        restartJob = scheduler.schedule(() -> {
            // Start the mesh monitor
            meshMonitor = new ZigBeeNetworkMeshMonitor(networkManager);
            meshMonitor.startup(MESH_UPDATE_TIME);
        }, MESH_UPDATE_TIME, TimeUnit.SECONDS);
    }

    private void startZigBeeNetwork() {
        logger.debug("Scheduling ZigBee start");
        restartJob = scheduler.schedule(() -> {
            logger.debug("ZigBee network starting");
            restartJob = null;
            initialiseZigBee();
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public void handleRemoval() {
        // this flag is used in dispose() in order delete the network state file when the bridge has been removed
        bridgeRemoved = true;
        super.handleRemoval();
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("{}: Configuration received (Coordinator).", nodeIeeeAddress);

        TransportConfig transportConfig = new TransportConfig();

        boolean reinitialise = false;
        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            switch (configurationParameter.getKey()) {
                case ZigBeeBindingConstants.CONFIGURATION_JOINENABLE:
                    if ((Boolean) configurationParameter.getValue()) {
                        permitJoin(nodeIeeeAddress, 60);
                    }
                    break;

                case ZigBeeBindingConstants.CONFIGURATION_TRUSTCENTREMODE:
                    TrustCentreJoinMode linkMode = TrustCentreJoinMode
                            .valueOf((String) configurationParameter.getValue());
                    transportConfig.addOption(TransportConfigOption.TRUST_CENTRE_JOIN_MODE, linkMode);
                    break;

                case ZigBeeBindingConstants.CONFIGURATION_TXPOWER:
                    transportConfig.addOption(TransportConfigOption.RADIO_TX_POWER, configurationParameter.getValue());
                    break;

                case ZigBeeBindingConstants.CONFIGURATION_POWERMODE:
                case ZigBeeBindingConstants.CONFIGURATION_BAUD:
                case ZigBeeBindingConstants.CONFIGURATION_FLOWCONTROL:
                case ZigBeeBindingConstants.CONFIGURATION_PORT:
                    reinitialise = true;
                    break;

                default:
                    configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
                    logger.debug("{}: Unhandled configuration parameter {} >> {}.", nodeIeeeAddress,
                            configurationParameter.getKey(), configurationParameter.getValue());
                    continue;
            }

            configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
        }

        // Persist changes
        updateConfiguration(configuration);

        // If we added any transport layer configuration, pass it down
        if (transportConfig.getOptions().size() != 0) {
            zigbeeTransport.updateTransportConfig(transportConfig);
        }

        // If we need to reinitialise the bridge to change driver parameters, do so
        if (reinitialise == true) {
            dispose();
            initialize();
        }
    }

    /**
     * Adds a {@link ZigBeeNetworkNodeListener} to receive updates on node status
     *
     * @param listener
     */
    public void addNetworkNodeListener(ZigBeeNetworkNodeListener listener) {
        // Save the listeners until the network is initialised
        synchronized (listeners) {
            listeners.add(listener);
        }

        if (networkManager == null) {
            return;
        }
        networkManager.addNetworkNodeListener(listener);
    }

    /**
     * Removes a {@link ZigBeeNetworkNodeListener} to receive updates on node status
     *
     * @param listener
     */
    public void removeNetworkNodeListener(ZigBeeNetworkNodeListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }

        if (networkManager == null) {
            return;
        }
        networkManager.removeNetworkNodeListener(listener);
    }

    @Override
    public void nodeAdded(ZigBeeNode node) {
        nodeUpdated(node);
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
                logger.error("Error updating configuration: Unable to set mac address. ", e);
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

    public ZigBeeEndpoint getEndpoint(IeeeAddress address, int endpointId) {
        if (networkManager == null) {
            return null;
        }
        ZigBeeNode node = networkManager.getNode(address);
        if (node == null) {
            return null;
        }
        return node.getEndpoint(endpointId);
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
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                break;
            default:
                break;
        }
    }

    /**
     * Gets a node given the long address
     *
     * @param nodeIeeeAddress the {@link IeeeAddress} of the device
     * @return the {@link ZigBeeNode} or null if the node is not found
     */
    public ZigBeeNode getNode(IeeeAddress nodeIeeeAddress) {
        if (networkManager == null) {
            return null;
        }
        return networkManager.getNode(nodeIeeeAddress);
    }

    /**
     * Gets the nodes in this network manager
     *
     * @return the set of {@link ZigBeeNode}s
     */
    public Set<ZigBeeNode> getNodes() {
        return networkManager.getNodes();
    }

    /**
     * Removes a node from the network manager. This does not cause the network manager to tell the node to leave the
     * network, but will only remove the node from the network manager lists. Thus, if the node is still alive, it may
     * be able to rejoin the network.
     * <p>
     * To force the node to leave the network, use the {@link #leave(IeeeAddress)} method
     *
     * @param nodeIeeeAddress the {@link IeeeAddress} of the node to remove
     */
    public void removeNode(IeeeAddress nodeIeeeAddress) {
        ZigBeeNode node = networkManager.getNode(nodeIeeeAddress);
        if (node == null) {
            return;
        }
        networkManager.removeNode(node);
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
        return true;
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

    /**
     * Serialize the network state
     */
    public void serializeNetwork() {
        if (networkStateSerializer != null) {
            networkStateSerializer.serialize(networkManager);
        }
    }

    public ZigBeeNetworkManager getNetworkManager() {
        return this.networkManager;
    }

    /**
     * Starts an active scan on the ZigBee coordinator.
     */
    public void scanStart() {
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            logger.debug("ZigBee coordinator is offline - aborted scan for {}", getThing().getUID());
            return;
        }

        networkManager.permitJoin(60);
    }

    /**
     * Stops an active scan on the ZigBee coordinator
     */
    public void scanStop() {
        networkManager.permitJoin(0);
    }

    @Override
    public @NonNull ThingUID getUID() {
        return getThing().getUID();
    }
}