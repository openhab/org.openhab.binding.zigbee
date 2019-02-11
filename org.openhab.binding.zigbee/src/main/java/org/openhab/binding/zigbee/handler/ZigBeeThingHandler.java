/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.ConfigDescription;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionProvider;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.binding.firmware.Firmware;
import org.eclipse.smarthome.core.thing.binding.firmware.FirmwareUpdateHandler;
import org.eclipse.smarthome.core.thing.binding.firmware.ProgressCallback;
import org.eclipse.smarthome.core.thing.binding.firmware.ProgressStep;
import org.eclipse.smarthome.core.thing.type.DynamicStateDescriptionProvider;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.StateDescription;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.discovery.ZigBeeNodePropertyDiscoverer;
import org.openhab.binding.zigbee.internal.ZigBeeDeviceConfigHandler;
import org.openhab.binding.zigbee.internal.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.internal.converter.config.ZclClusterConfigFactory;
import org.openhab.binding.zigbee.internal.converter.config.ZclClusterConfigHandler;
import org.openhab.binding.zigbee.internal.converter.config.ZclReportingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeAnnounceListener;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeNodeStatus;
import com.zsmartsystems.zigbee.ZigBeeProfileType;
import com.zsmartsystems.zigbee.app.otaserver.ZclOtaUpgradeServer;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaFile;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaServerStatus;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaStatusCallback;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster;
import com.zsmartsystems.zigbee.zdo.field.NeighborTable;
import com.zsmartsystems.zigbee.zdo.field.RoutingTable;

/**
 * The standard ZigBee thing handler.
 *
 * @author Chris Jackson - Initial Contribution
 * @author Thomas Höfer - Injected ZigBeeChannelConverterFactory via constructor
 */
public class ZigBeeThingHandler extends BaseThingHandler implements ZigBeeNetworkNodeListener, ZigBeeAnnounceListener,
        FirmwareUpdateHandler, ConfigDescriptionProvider, DynamicStateDescriptionProvider {
    /**
     * Our logger
     */
    private final Logger logger = LoggerFactory.getLogger(ZigBeeThingHandler.class);

    /**
     * The binding's {@link DynamicStateDescriptionProvider}
     */
    private final Map<ChannelUID, StateDescription> stateDescriptions = new ConcurrentHashMap<>();

    /**
     * The map of all the channels defined for this thing
     */
    private final Map<ChannelUID, ZigBeeBaseChannelConverter> channels = new HashMap<>();

    /**
     * A list of all the configuration handlers at node level.
     */
    private final List<ZclClusterConfigHandler> configHandlers = new ArrayList<>();

    /**
     * The configuration description if dynamically generated
     */
    private ConfigDescription configDescription;

    /**
     * The {@link IeeeAddress} for this device
     */
    private IeeeAddress nodeIeeeAddress = null;

    private ZigBeeCoordinatorHandler coordinatorHandler;

    private boolean nodeInitialised = false;

    private final Object pollingSync = new Object();
    private ScheduledFuture<?> pollingJob = null;
    private final int POLLING_PERIOD_MIN = 5;
    private final int POLLING_PERIOD_MAX = 86400;
    private final int POLLING_PERIOD_DEFAULT = 1800;
    private int pollingPeriod = POLLING_PERIOD_DEFAULT;

    private boolean firmwareUpdateInProgress = false;

    /**
     * A set of channels that have been linked to items. This is used to ensure we only poll channels that are linked to
     * keep network activity to a minimum.
     */
    private final Set<ChannelUID> thingChannelsPoll = new HashSet<>();

    /**
     * The factory to create the converters for the different channels.
     */
    private final ZigBeeChannelConverterFactory channelFactory;

    /**
     * Creates a ZigBee thing.
     *
     * @param zigbeeDevice   the {@link Thing}
     * @param channelFactory the {@link ZigBeeChannelConverterFactory} to be used to create the channels
     */
    public ZigBeeThingHandler(Thing zigbeeDevice, ZigBeeChannelConverterFactory channelFactory) {
        super(zigbeeDevice);
        this.channelFactory = channelFactory;
    }

    @Override
    public void initialize() {
        final String configAddress = (String) getConfig().get(ZigBeeBindingConstants.CONFIGURATION_MACADDRESS);
        logger.debug("{}: Initializing ZigBee thing handler {}", configAddress, getThing().getUID());

        if (configAddress == null || configAddress.length() == 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    ZigBeeBindingConstants.OFFLINE_NO_ADDRESS);
            return;
        }
        nodeIeeeAddress = new IeeeAddress(configAddress);

        // we do not know the current state of the device until our scheduled job has initialized the device
        updateStatus(ThingStatus.UNKNOWN);

        if (getBridge() != null) {
            bridgeStatusChanged(getBridge().getStatusInfo());
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        super.bridgeStatusChanged(bridgeStatusInfo);
        logger.debug("{}: Coordinator status changed to {}.", nodeIeeeAddress, bridgeStatusInfo.getStatus());

        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE || getBridge() == null) {
            logger.debug("{}: Coordinator is unknown or not online.", nodeIeeeAddress);

            // The bridge has gone offline. In order to avoid any issues with data that is cached in the converters
            // we will reinitialise the node, and all converters, when the bridge comes back online.
            nodeInitialised = false;

            stopPolling();
            return;
        }

        logger.debug("{}: Coordinator is ONLINE. Starting device initialisation.", nodeIeeeAddress);

        coordinatorHandler = (ZigBeeCoordinatorHandler) getBridge().getHandler();
        coordinatorHandler.addNetworkNodeListener(this);
        coordinatorHandler.addAnnounceListener(this);
        coordinatorHandler.rediscoverNode(nodeIeeeAddress);

        initialiseZigBeeNode();
    }

    private void initialiseZigBeeNode() {
        scheduler.schedule(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                doNodeInitialisation();
                return null;
            }
        }, 10, TimeUnit.MILLISECONDS);
    }

    private synchronized void doNodeInitialisation() {
        if (nodeInitialised) {
            return;
        }

        ZigBeeNode node = coordinatorHandler.getNode(nodeIeeeAddress);
        if (node == null) {
            logger.debug("{}: Node not found - deferring handler initialisation", nodeIeeeAddress);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, ZigBeeBindingConstants.OFFLINE_NODE_NOT_FOUND);
            return;
        }

        // Check if discovery is complete and we know all the services the node supports
        if (!node.isDiscovered()) {
            logger.debug("{}: Node has not finished discovery", nodeIeeeAddress);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE,
                    ZigBeeBindingConstants.OFFLINE_DISCOVERY_INCOMPLETE);
            return;
        }

        logger.debug("{}: Start initialising ZigBee Thing handler", nodeIeeeAddress);

        // Update the general properties
        ZigBeeNodePropertyDiscoverer propertyDiscoverer = new ZigBeeNodePropertyDiscoverer();
        propertyDiscoverer.setProperties(getThing().getProperties());
        Map<String, String> newProperties = propertyDiscoverer.getProperties(node);
        updateProperties(newProperties);

        // Clear the channels in case we are reinitialising
        channels.clear();

        // Get the configuration handlers applicable for the thing
        ZclClusterConfigFactory configFactory = new ZclClusterConfigFactory();
        for (ZigBeeEndpoint endpoint : coordinatorHandler.getNodeEndpoints(nodeIeeeAddress)) {
            List<ZclClusterConfigHandler> handlers = configFactory.getConfigHandlers(endpoint);
            configHandlers.addAll(handlers);
        }

        List<Channel> nodeChannels;

        if (getThing().getThingTypeUID().equals(ZigBeeBindingConstants.THING_TYPE_GENERIC_DEVICE)) {
            // Dynamically create the channels from the device
            // Process all the endpoints for this device and add all channels as derived from the supported clusters
            nodeChannels = new ArrayList<>();
            for (ZigBeeEndpoint endpoint : coordinatorHandler.getNodeEndpoints(nodeIeeeAddress)) {
                logger.debug("{}: Checking endpoint {} channels", nodeIeeeAddress, endpoint.getEndpointId());
                nodeChannels.addAll(channelFactory.getChannels(getThing().getUID(), endpoint));
            }
            logger.debug("{}: Dynamically created {} channels", nodeIeeeAddress, nodeChannels.size());

            try {
                List<ConfigDescriptionParameter> parameters = new ArrayList<ConfigDescriptionParameter>();

                for (ZclClusterConfigHandler handler : configHandlers) {
                    parameters.addAll(handler.getConfiguration());
                }

                configDescription = new ConfigDescription(new URI("thing:" + getThing().getUID()), parameters);
            } catch (IllegalArgumentException | URISyntaxException e) {
                logger.debug("Error creating URI for thing description:", e);
            }
        } else {
            // We already have the correct thing type so just use the channels
            nodeChannels = getThing().getChannels();
            logger.debug("{}: Using static definition with existing {} channels", nodeIeeeAddress, nodeChannels.size());
        }

        // Add statically defined endpoints and clusters
        for (Channel channel : nodeChannels) {
            // Process the channel properties
            Map<String, String> properties = channel.getProperties();
            int endpointId = Integer.parseInt(properties.get(ZigBeeBindingConstants.CHANNEL_PROPERTY_ENDPOINT));
            ZigBeeEndpoint endpoint = node.getEndpoint(endpointId);
            if (endpoint == null) {
                int profileId;
                if (properties.get(ZigBeeBindingConstants.CHANNEL_PROPERTY_PROFILEID) == null) {
                    profileId = ZigBeeProfileType.ZIGBEE_HOME_AUTOMATION.getKey();
                } else {
                    profileId = Integer.parseInt(properties.get(ZigBeeBindingConstants.CHANNEL_PROPERTY_PROFILEID));
                }

                logger.debug("{}: Creating statically defined device endpoint {} with profile {}", nodeIeeeAddress,
                        endpointId, ZigBeeProfileType.getByValue(profileId));
                endpoint = new ZigBeeEndpoint(node, endpointId);
                endpoint.setProfileId(profileId);
                node.addEndpoint(endpoint);
            }

            List<Integer> staticClusters;
            boolean modified = false;
            staticClusters = processClusterList(endpoint.getInputClusterIds(),
                    properties.get(ZigBeeBindingConstants.CHANNEL_PROPERTY_INPUTCLUSTERS));
            if (!staticClusters.isEmpty()) {
                logger.debug("{}: Forcing endpoint {} input clusters {}", nodeIeeeAddress, endpointId, staticClusters);
                endpoint.setInputClusterIds(staticClusters);
                modified = true;
            }

            staticClusters = processClusterList(endpoint.getOutputClusterIds(),
                    properties.get(ZigBeeBindingConstants.CHANNEL_PROPERTY_OUTPUTCLUSTERS));
            if (!staticClusters.isEmpty()) {
                logger.debug("{}: Forcing endpoint {} output clusters {}", nodeIeeeAddress, endpointId, staticClusters);
                endpoint.setOutputClusterIds(staticClusters);
                modified = true;
            }

            if (modified) {
                logger.debug("{}: Updating endpoint {}", nodeIeeeAddress, endpointId);
                node.updateEndpoint(endpoint);
            }
        }

        try {
            pollingPeriod = POLLING_PERIOD_MAX;

            // Check if the channels we've discovered are the same
            List<ChannelUID> oldChannelUidList = new ArrayList<ChannelUID>();
            for (Channel channel : getThing().getChannels()) {
                oldChannelUidList.add(channel.getUID());
            }
            List<ChannelUID> newChannelUidList = new ArrayList<ChannelUID>();
            for (Channel channel : nodeChannels) {
                newChannelUidList.add(channel.getUID());

                // Add the configuration from the existing channel into the new channel
                Channel currentChannel = getThing().getChannel(channel.getUID().toString());
                if (currentChannel != null) {
                    channel.getConfiguration().setProperties(currentChannel.getConfiguration().getProperties());
                }
            }

            if (!newChannelUidList.equals(oldChannelUidList)) {
                logger.debug("{}: Updating thing definition as channels have changed from {} to {}", nodeIeeeAddress,
                        oldChannelUidList, newChannelUidList);
                ThingBuilder thingBuilder = editThing();
                thingBuilder.withChannels(nodeChannels).withConfiguration(getConfig());
                updateThing(thingBuilder.build());
            }

            // Create the channel map to simplify processing incoming events
            for (Channel channel : getThing().getChannels()) {
                // Process the channel properties
                Map<String, String> properties = channel.getProperties();

                ZigBeeBaseChannelConverter handler = channelFactory.createConverter(this, channel, coordinatorHandler,
                        node.getIeeeAddress(),
                        Integer.parseInt(properties.get(ZigBeeBindingConstants.CHANNEL_PROPERTY_ENDPOINT)));
                if (handler == null) {
                    logger.debug("{}: No handler found for {}", nodeIeeeAddress, channel.getUID());
                    continue;
                }

                logger.debug("{}: Initializing channel {} with {}", nodeIeeeAddress, channel.getUID(), handler);
                if (handler.initializeDevice() == false) {
                    logger.info("{}: Channel {} failed to initialise device", nodeIeeeAddress, channel.getUID());
                    continue;
                }
                if (handler.initializeConverter() == false) {
                    logger.info("{}: Channel {} failed to initialise converter", nodeIeeeAddress, channel.getUID());
                    continue;
                }

                if (channel.getConfiguration().get(ZclReportingConfig.CONFIG_POLLING) == null) {
                    channel.getConfiguration().put(ZclReportingConfig.CONFIG_POLLING, handler.getPollingPeriod());
                }

                handler.handleRefresh();

                // TODO: Update the channel configuration from the device if method available
                handler.updateConfiguration(new Configuration(), channel.getConfiguration().getProperties());

                channels.put(channel.getUID(), handler);

                if (handler.getPollingPeriod() < pollingPeriod) {
                    pollingPeriod = handler.getPollingPeriod();
                }

                // Provide the state descriptions if the channel provides them
                StateDescription stateDescription = handler.getStateDescription();
                if (stateDescription != null) {
                    stateDescriptions.put(channel.getUID(), stateDescription);
                }
            }
        } catch (Exception e) {
            logger.error("{}: Exception creating channels ", nodeIeeeAddress, e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR);
            return;
        }
        logger.debug("{}: Channel initialisation complete", nodeIeeeAddress);

        // Update the binding table.
        // We're not doing anything with the information here, but we want it up to date so it's ready for use later.
        try {
            if (node.updateBindingTable().get() == false) {
                logger.debug("{}: Error getting binding table", nodeIeeeAddress);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception getting binding table ", nodeIeeeAddress, e);
        }

        updateStatus(ThingStatus.ONLINE);

        startPolling();

        nodeInitialised = true;

        logger.debug("{}: Done initialising ZigBee Thing handler", nodeIeeeAddress);

        // Save the network state
        coordinatorHandler.serializeNetwork();
    }

    /**
     * Process a static cluster list and add it to the existing list
     *
     * @param initialClusters a collection of existing clusters
     * @param newClusters     a string containing a comma separated list of clusters
     * @return a list of clusters if the list is updated, or an empty list if it has not changed
     */
    private List<Integer> processClusterList(Collection<Integer> initialClusters, String newClusters) {
        if (newClusters == null || newClusters.length() == 0) {
            return Collections.emptyList();
        }

        Set<Integer> clusters = new HashSet<Integer>();
        clusters.addAll(initialClusters);
        return clusters.addAll(
                Arrays.asList(newClusters.split(",")).stream().map(s -> new Integer(s)).collect(Collectors.toSet()))
                        ? new ArrayList<Integer>(clusters)
                        : Collections.emptyList();
    }

    @Override
    public void dispose() {
        logger.debug("{}: Handler dispose.", nodeIeeeAddress);

        stopPolling();

        if (nodeIeeeAddress != null) {
            if (coordinatorHandler != null) {
                coordinatorHandler.removeNetworkNodeListener(this);
                coordinatorHandler.removeAnnounceListener(this);
            }
            nodeIeeeAddress = null;
        }

        for (ZigBeeBaseChannelConverter channel : channels.values()) {
            channel.disposeConverter();
        }
        channels.clear();

        nodeInitialised = false;
    }

    private void stopPolling() {
        synchronized (pollingSync) {
            if (pollingJob != null) {
                pollingJob.cancel(true);
                pollingJob = null;
                logger.debug("{}: Polling stopped", nodeIeeeAddress);
            }
        }
    }

    /**
     * Start polling channel updates
     */
    private void startPolling() {
        Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    logger.debug("{}: Polling...", nodeIeeeAddress);

                    for (ChannelUID channelUid : channels.keySet()) {
                        if (!thingChannelsPoll.contains(channelUid)) {
                            // Don't poll if this channel isn't linked
                            continue;
                        }

                        logger.debug("{}: Polling {}", nodeIeeeAddress, channelUid);
                        ZigBeeBaseChannelConverter converter = channels.get(channelUid);
                        if (converter == null) {
                            logger.debug("{}: Polling aborted as no converter found for {}", nodeIeeeAddress,
                                    channelUid);
                        } else {
                            converter.handleRefresh();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("{}: Polling aborted due to exception ", nodeIeeeAddress, e);
                }
            }
        };

        synchronized (pollingSync) {
            stopPolling();

            if (pollingPeriod < POLLING_PERIOD_MIN) {
                logger.debug("{}: Polling period was set below minimum value. Using minimum.", nodeIeeeAddress);
                pollingPeriod = POLLING_PERIOD_MIN;
            }

            if (pollingPeriod > POLLING_PERIOD_MAX) {
                logger.debug("{}: Polling period was set above maximum value. Using maximum.", nodeIeeeAddress);
                pollingPeriod = POLLING_PERIOD_MAX;
            }

            // Polling starts almost immediately to get an immediate refresh
            // Add some random element to the period so that all things aren't synchronised
            int pollingPeriodMs = pollingPeriod * 1000 + new Random().nextInt(pollingPeriod * 100);
            pollingJob = scheduler.scheduleAtFixedRate(pollingRunnable, new Random().nextInt(pollingPeriodMs),
                    pollingPeriodMs, TimeUnit.MILLISECONDS);
            logger.debug("{}: Polling initialised at {}ms", nodeIeeeAddress, pollingPeriodMs);
        }
    }

    @Override
    public void deviceStatusUpdate(ZigBeeNodeStatus deviceStatus, Integer networkAddress, IeeeAddress ieeeAddress) {
        // A node has joined - or come back online
        if (!nodeIeeeAddress.equals(ieeeAddress)) {
            return;
        }

        // Use this to update channel information - eg bulb state will likely change when the device was powered off/on.
        startPolling();
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        logger.debug("{}: Channel {} linked - polling started.", nodeIeeeAddress, channelUID);

        // We keep track of what channels are used and only poll channels that the framework is using
        thingChannelsPoll.add(channelUID);
    }

    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        logger.debug("{}: Channel {} unlinked - polling stopped.", nodeIeeeAddress, channelUID);

        // We keep track of what channels are used and only poll channels that the framework is using
        thingChannelsPoll.remove(channelUID);
    }

    @Override
    public void handleRemoval() {
        coordinatorHandler.leave(nodeIeeeAddress);
        coordinatorHandler.removeNode(nodeIeeeAddress);
        updateStatus(ThingStatus.REMOVED);
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("{}: Configuration received: {}", nodeIeeeAddress, configurationParameters);

        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            // Ignore any configuration parameters that have not changed
            if (Objects.equals(configurationParameter.getValue(), configuration.get(configurationParameter.getKey()))) {
                logger.debug("{}: Configuration update: Ignored {} as no change", nodeIeeeAddress,
                        configurationParameter.getKey());
                continue;
            }

            switch (configurationParameter.getKey()) {
                case ZigBeeBindingConstants.CONFIGURATION_JOINENABLE:
                    coordinatorHandler.permitJoin(nodeIeeeAddress, 60);
                    break;
                case ZigBeeBindingConstants.CONFIGURATION_LEAVE:
                    coordinatorHandler.leave(nodeIeeeAddress);
                    break;
                default:
                    break;
            }
        }

        for (ZclClusterConfigHandler handler : configHandlers) {
            handler.updateConfiguration(configuration, configurationParameters);
        }

        ZigBeeNode node = coordinatorHandler.getNode(nodeIeeeAddress);
        ZigBeeDeviceConfigHandler deviceConfigHandler = new ZigBeeDeviceConfigHandler(node);
        deviceConfigHandler.updateConfiguration(configuration, configurationParameters);

        // Persist changes
        updateConfiguration(configuration);
    }

    @Override
    public void handleCommand(final ChannelUID channelUID, final Command command) {
        logger.debug("{}: Command for channel {} --> {} [{}]", nodeIeeeAddress, channelUID, command,
                command.getClass().getSimpleName());

        // Check that we have a coordinator to work through
        if (coordinatorHandler == null) {
            logger.debug("{}: Coordinator handler not found. Cannot handle command without coordinator.",
                    nodeIeeeAddress);
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        ZigBeeBaseChannelConverter handler = channels.get(channelUID);
        if (handler == null) {
            logger.debug("{}: No handler found for {}", nodeIeeeAddress, channelUID);
            return;
        }

        Runnable commandHandler = new Runnable() {
            @Override
            public void run() {
                try {
                    if (command == RefreshType.REFRESH) {
                        handler.handleRefresh();
                    } else {
                        handler.handleCommand(command);
                    }
                } catch (Exception e) {
                    logger.debug("{}: Exception sending command to channel {}", nodeIeeeAddress, channelUID, e);
                }
            }
        };
        scheduler.schedule(commandHandler, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public @Nullable StateDescription getStateDescription(Channel channel,
            @Nullable StateDescription originalStateDescription, @Nullable Locale locale) {
        if (stateDescriptions.containsKey(channel.getUID())) {
            logger.trace("Returning new stateDescription for {}", channel.getUID());
            return stateDescriptions.get(channel.getUID());
        } else {
            return null;
        }
    }

    /**
     * Callback from handlers to update a channel state. This is called from the channel converter when the state
     * changes.
     *
     * @param channel the {@link ChannelUID} to be updated
     * @param state   the new {link State}
     */
    public void setChannelState(ChannelUID channel, State state) {
        if (firmwareUpdateInProgress) {
            logger.debug("Omitting updating ZigBee channel state {} to {} due to firmware update in progress", channel,
                    state);
            return;
        }
        logger.debug("{}: Updating ZigBee channel state {} to {}", nodeIeeeAddress, channel, state);
        updateState(channel, state);
        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * Callback from handlers to trigger a channel. This is called from the channel converter when a trigger is
     * received.
     *
     * @param channel the {@link ChannelUID} to be triggered
     * @param event   the event to be emitted
     */
    @Override
    public void triggerChannel(ChannelUID channel, String event) {
        if (firmwareUpdateInProgress) {
            logger.debug("Omitting triggering ZigBee channel {} with event {} due to firmware update in progress",
                    channel, event);
            return;
        }
        logger.debug("{}: Triggering ZigBee channel {} with event {}", nodeIeeeAddress, channel, event);
        super.triggerChannel(channel, event);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void nodeAdded(ZigBeeNode node) {
        nodeUpdated(node);
    }

    @Override
    public void nodeUpdated(ZigBeeNode node) {
        // Make sure it's our node that's updated
        if (!node.getIeeeAddress().equals(nodeIeeeAddress)) {
            return;
        }

        logger.debug("{}: Node updated - {}", nodeIeeeAddress, node);

        Map<String, String> properties = editProperties();

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append('[');
        boolean first = true;
        for (NeighborTable neighbor : node.getNeighbors()) {
            if (!first) {
                jsonBuilder.append(',');
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
        jsonBuilder.append(']');
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_NEIGHBORS, jsonBuilder.toString());

        jsonBuilder = new StringBuilder();
        jsonBuilder.append('[');
        first = true;
        for (RoutingTable route : node.getRoutes()) {
            if (!first) {
                jsonBuilder.append(',');
            }
            first = false;

            Map<String, Object> object = new HashMap<String, Object>();
            object.put("destination", route.getDestinationAddress());
            object.put("next_hop", route.getNextHopAddress());
            object.put("state", route.getStatus());
            jsonBuilder.append(ZigBeeBindingConstants.propertiesToJson(object));
        }
        jsonBuilder.append(']');

        properties.put(ZigBeeBindingConstants.THING_PROPERTY_ROUTES, jsonBuilder.toString());
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_ASSOCIATEDDEVICES, node.getAssociatedDevices().toString());
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_LASTUPDATE,
                ZigBeeBindingConstants.getISO8601StringForDate(node.getLastUpdateTime()));
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_NETWORKADDRESS, node.getNetworkAddress().toString());
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_STACKCOMPLIANCE,
                Integer.toString(node.getNodeDescriptor().getStackCompliance()));

        updateProperties(properties);

        initialiseZigBeeNode();
    }

    @Override
    public void nodeRemoved(ZigBeeNode node) {
        // Make sure it's our node that's updated
        if (!node.getIeeeAddress().equals(nodeIeeeAddress)) {
            return;
        }

        // Clear some properties
        Map<String, String> properties = editProperties();
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_LASTUPDATE, "");
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_ROUTES, "[]");
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_NEIGHBORS, "[]");
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_ASSOCIATEDDEVICES, "[]");
        updateProperties(properties);

        if (getThing().getStatus() != ThingStatus.REMOVING) {
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    @Override
    public Collection<ConfigDescription> getConfigDescriptions(Locale locale) {
        return Collections.emptySet();
    }

    @Override
    public ConfigDescription getConfigDescription(URI uri, Locale locale) {
        if (configDescription != null && configDescription.getUID().equals(uri)) {
            return configDescription;
        }

        if ("channel".equals(uri.getScheme()) == false) {
            return null;
        }

        ChannelUID channelUID = new ChannelUID(uri.getSchemeSpecificPart());

        // Is this a zigbee thing?
        if (!channelUID.getBindingId().equals(ZigBeeBindingConstants.BINDING_ID)) {
            return null;
        }

        // Do we know this channel?
        if (channels.get(channelUID) == null) {
            return null;
        }

        ZigBeeBaseChannelConverter converter = channels.get(channelUID);

        return new ConfigDescription(uri, converter.getConfigDescription());
    }

    @Override
    public void updateFirmware(Firmware firmware, ProgressCallback progressCallback) {
        logger.debug("{}: Update firmware with {}", nodeIeeeAddress, firmware.getVersion());

        // Find an OTA client if the device supports OTA upgrades
        ZigBeeNode node = coordinatorHandler.getNode(nodeIeeeAddress);
        if (node == null) {
            logger.debug("{}: Can't find node", nodeIeeeAddress);
            return;
        }

        ZclOtaUpgradeServer otaServer = null;
        ZigBeeEndpoint otaEndpoint = null;
        for (ZigBeeEndpoint endpoint : node.getEndpoints()) {
            otaServer = (ZclOtaUpgradeServer) endpoint.getApplication(ZclOtaUpgradeCluster.CLUSTER_ID);
            if (otaServer != null) {
                break;
            }

            if (endpoint.getOutputCluster(ZclOtaUpgradeCluster.CLUSTER_ID) != null) {
                otaEndpoint = endpoint;
                break;
            }
        }

        if (otaServer == null && otaEndpoint == null) {
            logger.debug("{}: Can't find OTA cluster", nodeIeeeAddress);
            return;
        }

        if (otaServer == null) {
            // Create the OTA server and register it with the endpoint
            otaServer = new ZclOtaUpgradeServer();
            otaEndpoint.addApplication(otaServer);
        }

        // Set ourselves offline, and prevent going back online
        firmwareUpdateInProgress = true;
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.FIRMWARE_UPDATING);

        // Define the sequence of the firmware update so that external consumers can listen for the progress
        progressCallback.defineSequence(ProgressStep.TRANSFERRING, ProgressStep.REBOOTING);

        ZigBeeOtaFile otaFile = new ZigBeeOtaFile(firmware.getBytes());
        otaServer.setFirmware(otaFile);

        // DOWNLOADING
        progressCallback.next();

        final ZclOtaUpgradeServer finalOtaServer = otaServer;
        otaServer.addListener(new ZigBeeOtaStatusCallback() {
            @Override
            public void otaStatusUpdate(ZigBeeOtaServerStatus status, int percent) {
                logger.debug("{}: OTA transfer status update {}, percent={}", nodeIeeeAddress, status, percent);
                switch (status) {
                    case OTA_WAITING:
                        // DOWNLOADING
                        progressCallback.next();
                        return;
                    case OTA_TRANSFER_IN_PROGRESS:
                        progressCallback.update(percent);
                        return;
                    case OTA_TRANSFER_COMPLETE:
                        // REBOOTING
                        progressCallback.next();
                        progressCallback.update(100);
                        return;
                    case OTA_UPGRADE_COMPLETE:
                        progressCallback.success();
                        break;
                    case OTA_UPGRADE_FAILED:
                        progressCallback.failed("zigbee.firmware.failed");
                        break;
                    case OTA_CANCELLED:
                        progressCallback.canceled();
                        break;
                    default:
                        return;
                }

                // OTA transfer is complete, cancelled or failed
                firmwareUpdateInProgress = false;
                finalOtaServer.cancelUpgrade();

                for (int retry = 0; retry < 3; retry++) {
                    Integer fileVersion = finalOtaServer.getCurrentFileVersion();
                    if (fileVersion != null) {
                        updateProperty(Thing.PROPERTY_FIRMWARE_VERSION, String.format("%s%08X",
                                ZigBeeBindingConstants.FIRMWARE_VERSION_HEX_PREFIX, fileVersion));
                        break;
                    } else {
                        logger.debug("{}: OTA firmware request timeout (retry {})", node.getIeeeAddress(), retry);
                    }
                }

                updateStatus(ThingStatus.ONLINE);
            }
        });
    }

    @Override
    public void cancel() {
        logger.debug("{}: Cancel firmware update", nodeIeeeAddress);
    }

    @Override
    public boolean isUpdateExecutable() {
        // Always allow the firmware to be updated
        return true;
    }
}
