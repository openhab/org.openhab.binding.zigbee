/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.handler;

import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.internal.ZigBeeDataStore;
import org.openhab.core.common.registry.Identifiable;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ExtendedPanId;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeAnnounceListener;
import com.zsmartsystems.zigbee.ZigBeeChannel;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNetworkState;
import com.zsmartsystems.zigbee.ZigBeeNetworkStateListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeProfileType;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension;
import com.zsmartsystems.zigbee.app.iasclient.ZigBeeIasCieExtension;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaUpgradeExtension;
import com.zsmartsystems.zigbee.security.MmoHash;
import com.zsmartsystems.zigbee.security.ZigBeeKey;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.serialization.ZigBeeDeserializer;
import com.zsmartsystems.zigbee.serialization.ZigBeeSerializer;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.TrustCentreJoinMode;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareUpdate;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster;
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
@NonNullByDefault
public abstract class ZigBeeCoordinatorHandler extends BaseBridgeHandler
        implements Identifiable<ThingUID>, ZigBeeNetworkStateListener, ZigBeeNetworkNodeListener {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeCoordinatorHandler.class);

    @Nullable
    protected Integer panId;
    @Nullable
    protected Integer channelId;
    @Nullable
    protected ExtendedPanId extendedPanId;

    @Nullable
    private IeeeAddress nodeIeeeAddress;

    @Nullable
    protected ZigBeeTransportTransmit zigbeeTransport;
    @Nullable
    private ZigBeeNetworkManager networkManager;

    @Nullable
    private Class<?> serializerClass;
    @Nullable
    private Class<?> deserializerClass;

    @Nullable
    private ZigBeeDataStore networkDataStore;

    @Nullable
    protected ZigBeeKey linkKey;
    @Nullable
    protected ZigBeeKey networkKey;

    @Nullable
    private TransportConfig transportConfig;

    private final Set<ZigBeeNetworkNodeListener> nodeListeners = new HashSet<>();
    private final Set<ZigBeeAnnounceListener> announceListeners = new HashSet<>();

    private final int MESH_UPDATE_PERIOD = 86400;

    /**
     * Set to true on startup if we want to reinitialize the network
     */
    private boolean initializeNetwork = false;

    @Nullable
    private ScheduledFuture<?> restartJob;

    private volatile boolean bridgeRemoved = false;

    @Nullable
    private ScheduledFuture<?> reconnectPollingTimer;
    @Nullable
    private ScheduledExecutorService reconnectPollingScheduler;
    private final Object reconnectLock = new Object();
    private boolean currentReconnectAttemptFinished = false;

    /**
     * The factory to create the converters for the different channels.
     */
    private final ZigBeeChannelConverterFactory channelFactory;

    /**
     * Default ZigBeeAlliance09 link key
     */
    private final static ZigBeeKey KEY_ZIGBEE_ALLIANCE_O9 = new ZigBeeKey(new int[] { 0x5A, 0x69, 0x67, 0x42, 0x65,
            0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39 });

    private static final long RECONNECT_RATE = 5;

    public ZigBeeCoordinatorHandler(Bridge coordinator, ZigBeeChannelConverterFactory channelFactory) {
        super(coordinator);
        this.channelFactory = channelFactory;
    }

    /**
     * Method called to initialise the dongle. Note that this may be called multiple times if the dongle is
     * reinitialised
     */
    protected abstract void initializeDongle();

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
            logger.debug("Config: {} found, initializeNetwork={}", CONFIGURATION_INITIALIZE, initializeNetwork);
        } else {
            initializeNetwork = true;
            logger.debug("Config: {} not found, initializeNetwork={} ", CONFIGURATION_INITIALIZE, initializeNetwork);
        }

        if (extendedPanId == null || extendedPanId.equals(new ExtendedPanId()) || panId == 0) {
            initializeNetwork = true;
            logger.debug("ExtendedPanId or PanId not set: initializeNetwork={}", initializeNetwork);
        }

        // Process the network key
        try {
            logger.debug("Network Key String {}", networkKeyString);
            networkKey = new ZigBeeKey(networkKeyString);
        } catch (IllegalArgumentException e) {
            networkKey = new ZigBeeKey();
            logger.debug("Network Key String has invalid format. Revert to default key.");
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
                // Reset the initialization flag and save the keys
                Configuration configuration = editConfiguration();
                configuration.put(CONFIGURATION_LINKKEY, linkKey.toString());
                configuration.put(CONFIGURATION_NETWORKKEY, networkKey.toString());
                configuration.put(CONFIGURATION_INITIALIZE, false);
                updateConfiguration(configuration);
            } catch (IllegalStateException e) {
                logger.error("Error updating configuration: Unable to reset initialize flag and save keys. ", e);
            }
        }

        logger.debug("Link key final array {}", linkKey);

        initializeDongle();

        reconnectPollingScheduler = Executors.newSingleThreadScheduledExecutor();
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

        // shutdown reconnect task
        if (reconnectPollingTimer != null) {
            reconnectPollingTimer.cancel(true);
        }

        if (networkManager != null) {
            synchronized (nodeListeners) {
                for (ZigBeeNetworkNodeListener listener : nodeListeners) {
                    networkManager.removeNetworkNodeListener(listener);
                }
            }
            synchronized (announceListeners) {
                for (ZigBeeAnnounceListener listener : announceListeners) {
                    networkManager.removeAnnounceListener(listener);
                }
            }

            // Shut down the ZigBee library
            networkManager.shutdown();
        }

        if (networkDataStore != null && bridgeRemoved) {
            networkDataStore.delete();
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
     *
     * synchronized to avoid executing this if a reconnect is still in progress
     */
    private synchronized void initialiseZigBee() {
        logger.debug("Initialising ZigBee coordinator");

        String networkId = getThing().getUID().toString().replaceAll(":", "_");

        networkManager = new ZigBeeNetworkManager(zigbeeTransport);
        networkDataStore = new ZigBeeDataStore(networkId);

        // Configure the network manager
        networkManager.setNetworkDataStore(networkDataStore);
        networkManager.setSerializer(serializerClass, deserializerClass);
        networkManager.addNetworkStateListener(this);
        networkManager.addNetworkNodeListener(this);

        // Initialise the network
        ZigBeeStatus initializeResponse = networkManager.initialize();

        // Firmware Version should be available at this point.
        // Present it to the users even if the initialse was unsuccessful
        Map<String, String> properties = editProperties();
        if (zigbeeTransport instanceof ZigBeeTransportFirmwareUpdate) {
            ZigBeeTransportFirmwareUpdate firmwareTransport = (ZigBeeTransportFirmwareUpdate) zigbeeTransport;
            properties.put(Thing.PROPERTY_FIRMWARE_VERSION, firmwareTransport.getFirmwareVersion());
        }
        if (networkManager.getLocalIeeeAddress() == null) {
            properties.remove(ZigBeeBindingConstants.THING_PROPERTY_MACADDRESS);
        } else {
            properties.put(ZigBeeBindingConstants.THING_PROPERTY_MACADDRESS,
                    networkManager.getLocalIeeeAddress().toString());
        }
        updateProperties(properties);

        switch (initializeResponse) {
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

        int meshUpdateTime = MESH_UPDATE_PERIOD;
        if (getConfig().get(CONFIGURATION_MESHUPDATEPERIOD) != null) {
            logger.debug("Mesh Update Period {}", getConfig().get(CONFIGURATION_MESHUPDATEPERIOD));
            meshUpdateTime = ((BigDecimal) getConfig().get(CONFIGURATION_MESHUPDATEPERIOD)).intValue();
        }

        // Add the extensions to the network
        ZigBeeDiscoveryExtension discoveryExtension = new ZigBeeDiscoveryExtension();
        discoveryExtension.setUpdatePeriod(meshUpdateTime);
        networkManager.addExtension(discoveryExtension);

        networkManager.addExtension(new ZigBeeIasCieExtension());
        networkManager.addExtension(new ZigBeeOtaUpgradeExtension());

        // Add any listeners that were registered before the manager was registered
        synchronized (nodeListeners) {
            for (ZigBeeNetworkNodeListener listener : nodeListeners) {
                networkManager.addNetworkNodeListener(listener);
            }
        }
        synchronized (announceListeners) {
            for (ZigBeeAnnounceListener listener : announceListeners) {
                networkManager.addAnnounceListener(listener);
            }
        }

        // Add all the clusters that we are supporting.
        // If we don't do this, the framework will reject any packets for clusters we have not stated support for.
        channelFactory.getImplementedClientClusters().stream()
                .forEach(clusterId -> networkManager.addSupportedClientCluster(clusterId));
        channelFactory.getImplementedServerClusters().stream()
                .forEach(clusterId -> networkManager.addSupportedServerCluster(clusterId));

        networkManager.addSupportedClientCluster(ZclBasicCluster.CLUSTER_ID);
        networkManager.addSupportedClientCluster(ZclOtaUpgradeCluster.CLUSTER_ID);
        networkManager.addSupportedServerCluster(ZclBasicCluster.CLUSTER_ID);

        // Show the initial network configuration for debugging
        ZigBeeChannel currentChannel = networkManager.getZigBeeChannel();
        int currentPanId = networkManager.getZigBeePanId();
        ExtendedPanId currentExtendedPanId = networkManager.getZigBeeExtendedPanId();

        logger.debug("ZigBee Initialise: Previous device configuration was: channel={}, PanID={}, EPanId={}",
                currentChannel, currentPanId, currentExtendedPanId);

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
            logger.debug("Config: {}={}", CONFIGURATION_TRUSTCENTREMODE, mode);
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
        nodeIeeeAddress = networkManager.getLocalIeeeAddress();
        currentChannel = networkManager.getZigBeeChannel();
        currentPanId = networkManager.getZigBeePanId();
        currentExtendedPanId = networkManager.getZigBeeExtendedPanId();
        logger.debug("ZigBee initialise done. channel={}, PanId={}  EPanId={}", currentChannel, currentPanId,
                currentExtendedPanId);

        // Set initializeNetwork to false to ensure that if communications to the dongle restarts, we don't reinitialise
        // the network again!
        initializeNetwork = false;

        // Set initializeNetwork to false to ensure that if communications to the dongle restarts, we don't reinitialise
        // the network again!
        initializeNetwork = false;

        initializeDongleSpecific();
    }

    private void startZigBeeNetwork() {
        logger.debug("Scheduling ZigBee start");
        restartJob = scheduler.schedule(() -> {
            logger.debug("ZigBee network starting");
            restartJob = null;
            initialiseZigBee();
        }, 1, TimeUnit.SECONDS);
    }

    private void startReconnectJobIfNotRunning() {
        if (reconnectPollingTimer != null) {
            return;
        }

        reconnectPollingTimer = reconnectPollingScheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                ZigBeeNetworkState state = networkManager.getNetworkState();
                if (state == ZigBeeNetworkState.ONLINE || state == ZigBeeNetworkState.INITIALISING) {
                    return;
                }

                logger.info("ZigBee dongle inactivity timer. Reinitializing ZigBee");

                // Close everything that has been started prior to initializing the serial port
                if (restartJob != null) {
                    restartJob.cancel(true);
                }
                networkManager.shutdown();

                synchronized (reconnectLock) {
                    currentReconnectAttemptFinished = false;
                }

                // Initialize the network again
                initializeDongle();

                waitForReconnectAttemptToFinish();
            }

            private void waitForReconnectAttemptToFinish() {
                synchronized (reconnectLock) {
                    try {
                        while (!currentReconnectAttemptFinished) {
                            reconnectLock.wait();
                        }
                    } catch (InterruptedException e) {
                        // thread may be killed if callback reports that we are connected again
                    }
                }
            }
        }, 1, RECONNECT_RATE, TimeUnit.SECONDS);
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
            // Ignore any configuration parameters that have not changed
            if (Objects.equals(configurationParameter.getValue(), configuration.get(configurationParameter.getKey()))) {
                logger.debug("{}: Configuration update: Ignored {} as no change", nodeIeeeAddress,
                        configurationParameter.getKey());
                continue;
            }

            logger.debug("{}: Configuration update: Processing {} -> {}", nodeIeeeAddress,
                    configurationParameter.getKey(), configurationParameter.getValue());

            boolean saveConfig = true;
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

                case ZigBeeBindingConstants.CONFIGURATION_CHANNEL:
                    networkManager.setZigBeeChannel(ZigBeeChannel.create(channelId));
                    break;

                case ZigBeeBindingConstants.CONFIGURATION_LINKKEY:
                case ZigBeeBindingConstants.CONFIGURATION_NETWORKKEY:
                case ZigBeeBindingConstants.CONFIGURATION_POWERMODE:
                case ZigBeeBindingConstants.CONFIGURATION_BAUD:
                case ZigBeeBindingConstants.CONFIGURATION_FLOWCONTROL:
                case ZigBeeBindingConstants.CONFIGURATION_PORT:
                case ZigBeeBindingConstants.CONFIGURATION_EXTENDEDPANID:
                case ZigBeeBindingConstants.CONFIGURATION_INITIALIZE:
                    reinitialise = true;
                    break;

                case ZigBeeBindingConstants.THING_PROPERTY_INSTALLCODE:
                    addInstallCode((String) configurationParameter.getValue());
                    // Don't save this - it's a transient key
                    saveConfig = false;
                    break;

                default:
                    configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
                    logger.debug("{}: Unhandled configuration parameter {} >> {}.", nodeIeeeAddress,
                            configurationParameter.getKey(), configurationParameter.getValue());
                    continue;
            }

            if (saveConfig) {
                configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
            }
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
     * Process the adding of an install code
     *
     * @param installCode the string representation of the install code
     * @param transportConfig the {@link TransportConfig} to populate with the configuration
     */
    private void addInstallCode(String installCode) {
        if (installCode == null || installCode.isEmpty()) {
            return;
        }

        // Split the install code and the address
        String[] codeParts = installCode.split(":");
        if (codeParts.length != 2) {
            logger.warn("{}: Incorrectly formatted install code configuration {}", nodeIeeeAddress, installCode);
            return;
        }

        MmoHash mmoHash = new MmoHash(codeParts[1].replace("-", ""));
        ZigBeeKey key = new ZigBeeKey(mmoHash.getHash());
        key.setAddress(new IeeeAddress(codeParts[0]));

        networkManager.setZigBeeInstallKey(key);
    }

    public void startDeviceDiscovery() {
        // Allow devices to join for 60 seconds
        networkManager.permitJoin(60);
    }

    /**
     * Adds a {@link ZigBeeNetworkNodeListener} to receive updates on node status
     *
     * @param listener the {@link ZigBeeNetworkNodeListener} to add
     */
    public void addNetworkNodeListener(ZigBeeNetworkNodeListener listener) {
        // Save the listeners until the network is initialised
        synchronized (nodeListeners) {
            nodeListeners.add(listener);
        }

        if (networkManager == null) {
            return;
        }
        networkManager.addNetworkNodeListener(listener);
    }

    /**
     * Removes a {@link ZigBeeNetworkNodeListener} to receive updates on node status
     *
     * @param listener the {@link ZigBeeNetworkNodeListener} to remove
     */
    public void removeNetworkNodeListener(ZigBeeNetworkNodeListener listener) {
        synchronized (nodeListeners) {
            nodeListeners.remove(listener);
        }

        if (networkManager == null) {
            return;
        }
        networkManager.removeNetworkNodeListener(listener);
    }

    /**
     * Adds a {@link ZigBeeAnnounceListener} to receive node announce messages
     *
     * @param listener the {@link ZigBeeAnnounceListener} to add
     */
    public void addAnnounceListener(ZigBeeAnnounceListener listener) {
        // Save the listeners until the network is initialised
        synchronized (announceListeners) {
            announceListeners.add(listener);
        }

        if (networkManager == null) {
            return;
        }
        networkManager.addAnnounceListener(listener);
    }

    /**
     * Removes a {@link ZigBeeAnnounceListener}
     *
     * @param listener the {@link ZigBeeAnnounceListener} to remove
     */
    public void removeAnnounceListener(ZigBeeAnnounceListener listener) {
        synchronized (announceListeners) {
            announceListeners.remove(listener);
        }

        if (networkManager == null) {
            return;
        }
        networkManager.removeAnnounceListener(listener);
    }

    @Override
    public void nodeAdded(@Nullable ZigBeeNode node) {
        nodeUpdated(node);
    }

    @Override
    public void nodeUpdated(@Nullable ZigBeeNode node) {
        // We're only interested in the coordinator here.
        if (node.getNetworkAddress() != 0) {
            return;
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

    @Nullable
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

    public IeeeAddress getLocalIeeeAddress() {
        return networkManager.getLocalIeeeAddress();
    }

    /**
     * Gets the local endpoint associated with the specified {@link ZigBeeProfileType}
     *
     * @param profile the {@link ZigBeeProfileType} of the endpoint
     * @return the endpoint ID
     */
    public int getLocalEndpointId(ZigBeeProfileType profile) {
        return 1;
    }

    @Override
    public void networkStateUpdated(@Nullable final ZigBeeNetworkState state) {
        logger.debug("{}: networkStateUpdated called with state={}", nodeIeeeAddress, state);
        switch (state) {
            case UNINITIALISED:
                break;
            case INITIALISING:
                break;
            case ONLINE:
                try {
                    // Persist the network configuration
                    ZigBeeChannel currentChannel = networkManager.getZigBeeChannel();
                    int currentPanId = networkManager.getZigBeePanId();
                    ExtendedPanId currentExtendedPanId = networkManager.getZigBeeExtendedPanId();

                    Configuration configuration = editConfiguration();
                    configuration.put(CONFIGURATION_PANID, currentPanId);
                    if (currentExtendedPanId != null) {
                        configuration.put(CONFIGURATION_EXTENDEDPANID, currentExtendedPanId.toString());
                    }
                    configuration.put(CONFIGURATION_CHANNEL, currentChannel.getChannel());

                    // If the thing is defined statically, then this will fail and we will never start!
                    updateConfiguration(configuration);
                } catch (IllegalStateException e) {
                    logger.error("Error updating configuration when network is ONLINE ", e);
                }

                updateStatus(ThingStatus.ONLINE);
                if (reconnectPollingTimer != null) {
                    reconnectPollingTimer.cancel(true);
                    reconnectPollingTimer = null;
                }
                break;
            case OFFLINE:
                Bridge bridge = getThing();

                // Do not try to reconnect if there is a firmware update in progress
                if (bridge.getStatus() == ThingStatus.OFFLINE
                        && bridge.getStatusInfo().getStatusDetail() == ThingStatusDetail.FIRMWARE_UPDATING) {
                    logger.debug("{}: Ignoring OFFLINE status: Thing is {}/{}", bridge.getStatus(),
                            bridge.getStatusInfo().getStatusDetail());
                    break;
                }

                // - Do not set the status to OFFLINE when the bridge is in one of these states. According to the
                // documentation https://www.eclipse.org/smarthome/documentation/concepts/things.html#status-transitions
                // the thing must not change from these statuses to OFFLINE
                // - Do not try to reconnect if the bridge is being removed.
                if (Arrays.asList(ThingStatus.UNINITIALIZED, ThingStatus.REMOVING, ThingStatus.REMOVED)
                        .contains(bridge.getStatus())) {
                    logger.debug("{}: Ignoring OFFLINE status: Thing is {}", bridge.getStatus());
                    break;
                }

                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                startReconnectJobIfNotRunning();
                break;
            default:
                break;
        }

        if (state != ZigBeeNetworkState.INITIALISING && state != ZigBeeNetworkState.UNINITIALISED) {
            notifyReconnectJobAboutFinishedInitialization();
        }
    }

    private void notifyReconnectJobAboutFinishedInitialization() {
        synchronized (reconnectLock) {
            currentReconnectAttemptFinished = true;
            reconnectLock.notifyAll();
        }
    }

    /**
     * Gets a node given the long address
     *
     * @param nodeIeeeAddress the {@link IeeeAddress} of the device
     * @return the {@link ZigBeeNode} or null if the node is not found
     */
    @Nullable
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
        if (networkManager == null) {
            return Collections.emptySet();
        }
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
     * Removes a node from the network manager and deletes the data store files. This does not cause the network manager
     * to tell the node to leave the network, and should only be used if the device has already left the network, or we
     * are permanently deleting it.
     *
     * @param nodeIeeeAddress the {@link IeeeAddress} of the node to delete
     */
    public void deleteNode(IeeeAddress nodeIeeeAddress) {
        removeNode(nodeIeeeAddress);
        networkDataStore.removeNode(nodeIeeeAddress);
    }

    /**
     * Permit joining only for the specified node
     *
     * @param address the 16 bit network address of the node to enable joining
     * @param duration the duration of the join
     */
    public boolean permitJoin(@Nullable IeeeAddress address, int duration) {
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
     * @param forceRemoval true to remove the node from the network, even if the leave request fails
     * @return true if the command is sent
     */
    public boolean leave(IeeeAddress address, boolean forceRemoval) {
        // First we want to make sure that join is disabled
        networkManager.permitJoin(0);

        logger.debug("{}: ZigBee leave command", address);
        ZigBeeNode node = networkManager.getNode(address);
        if (node == null) {
            logger.debug("{}: ZigBee leave command - node not found", address);
            return false;
        }

        logger.debug("{}: ZigBee leave command to {}", address, node.getNetworkAddress());

        networkManager.leave(node.getNetworkAddress(), node.getIeeeAddress(), forceRemoval);
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
     *
     * @param nodeAddress the {@link IeeeAddress} of the node to serialize
     */
    public void serializeNetwork(IeeeAddress nodeAddress) {
        if (networkManager != null) {
            networkManager.serializeNetworkDataStore(nodeAddress);
        }
    }

    /**
     * Gets the {@link ZigBeeNetworkManager} for the network
     *
     * @return the {@link ZigBeeNetworkManager}
     */
    @Nullable
    public ZigBeeNetworkManager getNetworkManager() {
        return networkManager;
    }

    /**
     * Starts an active scan on the ZigBee coordinator.
     *
     * @param joinTime the time to enable join - must be between 1 and 254 seconds.
     */
    public void scanStart(int joinTime) {
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            logger.debug("ZigBee coordinator is offline - aborted scan for {}", getThing().getUID());
            return;
        }

        networkManager.permitJoin(joinTime);
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
