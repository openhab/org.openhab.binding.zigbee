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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.discovery.ZigBeeNodePropertyDiscoverer;
import org.openhab.binding.zigbee.internal.ZigBeeConfigDescriptionParameters;
import org.openhab.binding.zigbee.internal.converter.config.ZclClusterConfigFactory;
import org.openhab.binding.zigbee.internal.converter.config.ZclClusterConfigHandler;
import org.openhab.core.config.core.ConfigDescriptionBuilder;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.ConfigDescriptionProvider;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.binding.firmware.FirmwareUpdateHandler;
import org.openhab.core.thing.type.DynamicStateDescriptionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeAnnounceListener;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeProfileType;

/**
 * The standard ZigBee thing handler.
 *
 * @author Chris Jackson - Initial Contribution
 */
public class ZigBeeGenericThingHandler extends ZigBeeBaseThingHandler implements ZigBeeNetworkNodeListener,
        ZigBeeAnnounceListener, FirmwareUpdateHandler, ConfigDescriptionProvider, DynamicStateDescriptionProvider {
    /**
     * Our logger
     */
    private final Logger logger = LoggerFactory.getLogger(ZigBeeGenericThingHandler.class);

    private boolean nodeInitialised = false;

    /**
     * Creates a ZigBee thing.
     *
     * @param zigbeeDevice the {@link Thing}
     * @param channelFactory the {@link ZigBeeChannelConverterFactory} to be used to create the channels
     * @param zigbeeIsAliveTracker the tracker which sets the {@link Thing} to OFFLINE after a period without
     *            communication
     */
    public ZigBeeGenericThingHandler(Thing zigbeeDevice, ZigBeeChannelConverterFactory channelFactory,
            ZigBeeIsAliveTracker zigbeeIsAliveTracker) {
        super(zigbeeDevice, channelFactory, zigbeeIsAliveTracker);
    }

    @Override
    protected synchronized void doNodeInitialisation(ZigBeeNode node) {
        if (nodeInitialised) {
            return;
        }

        if (node == null) {
            logger.debug("{}: Node not found - deferring handler initialisation", nodeIeeeAddress);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE, ZigBeeBindingConstants.OFFLINE_NODE_NOT_FOUND);
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

        List<ConfigDescriptionParameter> parameters = new ArrayList<>(
                ZigBeeConfigDescriptionParameters.getParameters());

        if (getThing().getThingTypeUID().equals(ZigBeeBindingConstants.THING_TYPE_GENERIC_DEVICE)) {
            // Dynamically create the channels from the device
            // Process all the endpoints for this device and add all channels as derived from the supported clusters
            nodeChannels = new ArrayList<>();
            for (ZigBeeEndpoint endpoint : coordinatorHandler.getNodeEndpoints(nodeIeeeAddress)) {
                logger.debug("{}: Checking endpoint {} channels", nodeIeeeAddress, endpoint.getEndpointId());
                nodeChannels.addAll(channelFactory.getChannels(getThing().getUID(), endpoint));
            }
            logger.debug("{}: Dynamically created {} channels", nodeIeeeAddress, nodeChannels.size());

            for (ZclClusterConfigHandler handler : configHandlers) {
                parameters.addAll(handler.getConfiguration());
            }
        } else {
            // We already have the correct thing type so just use the channels
            nodeChannels = getThing().getChannels();
            logger.debug("{}: Using static definition with existing {} channels", nodeIeeeAddress, nodeChannels.size());
        }

        try {
            configDescription = ConfigDescriptionBuilder.create(new URI("thing:" + getThing().getUID()))
                    .withParameters(parameters).build();
        } catch (IllegalArgumentException | URISyntaxException e) {
            logger.debug("Error creating URI for thing description:", e);
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

            boolean doInitializeDevice = !Boolean
                    .parseBoolean(thing.getProperties().get(ZigBeeBindingConstants.THING_PROPERTY_DEVICE_INITIALIZED));
            if (doInitializeDevice) {
                initializeDevice();
            } else {
                logger.debug("{}: Device initialization will be skipped as the device is already initialized",
                        nodeIeeeAddress);
            }
        } catch (Exception e) {
            logger.error("{}: Exception creating channels ", nodeIeeeAddress, e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR);
            return;
        }

        logger.debug("{}: Channel initialisation complete", nodeIeeeAddress);
    }

    /**
     * Process a static cluster list and add it to the existing list
     *
     * @param initialClusters a collection of existing clusters
     * @param newClusters a string containing a comma separated list of clusters
     * @return a list of clusters if the list is updated, or an empty list if it has not changed
     */
    private List<Integer> processClusterList(Collection<Integer> initialClusters, String newClusters) {
        if (newClusters == null || newClusters.length() == 0) {
            return Collections.emptyList();
        }

        Set<Integer> clusters = new HashSet<Integer>();
        clusters.addAll(initialClusters);
        return clusters.addAll(
                Arrays.asList(newClusters.split(",")).stream().map(s -> Integer.valueOf(s)).collect(Collectors.toSet()))
                        ? new ArrayList<Integer>(clusters)
                        : Collections.emptyList();
    }

}
