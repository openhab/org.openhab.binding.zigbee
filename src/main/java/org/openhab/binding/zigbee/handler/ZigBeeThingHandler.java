/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverter;
import org.openhab.binding.zigbee.discovery.ZigBeeNodePropertyDiscoverer;
import org.openhab.binding.zigbee.internal.ZigBeeActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeDevice;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zdo.descriptors.NeighborTable;
import com.zsmartsystems.zigbee.zdo.descriptors.RoutingTable;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeThingHandler extends BaseThingHandler implements ZigBeeNetworkNodeListener {
    private HashMap<ChannelUID, ZigBeeChannelConverter> channels = new HashMap<ChannelUID, ZigBeeChannelConverter>();

    private IeeeAddress nodeIeeeAddress = null;
    private int networkAddress;

    private Logger logger = LoggerFactory.getLogger(ZigBeeThingHandler.class);

    private ZigBeeCoordinatorHandler coordinatorHandler;

    private ScheduledFuture<?> pollingJob;

    private boolean nodeInitialised = false;

    private final TranslationProvider translationProvider;

    public ZigBeeThingHandler(Thing zigbeeDevice, TranslationProvider translationProvider) {
        super(zigbeeDevice);
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
        logger.debug("Initializing ZigBee thing handler {}.", getThing().getUID());
        final String configAddress = (String) getConfig().get(ZigBeeBindingConstants.CONFIGURATION_MACADDRESS);

        if (configAddress == null || configAddress.length() == 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    getI18nConstant(ZigBeeBindingConstants.OFFLINE_NO_ADDRESS));
            return;
        }
        nodeIeeeAddress = new IeeeAddress(configAddress);

        updateStatus(ThingStatus.OFFLINE);

        if (getBridge() != null) {
            bridgeStatusChanged(getBridge().getStatusInfo());
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("{}: Coordinator status changed to {}.", nodeIeeeAddress, bridgeStatusInfo.getStatus());

        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            logger.debug("{}: Coordinator is not online.", nodeIeeeAddress, bridgeStatusInfo.getStatus());
            return;
        }

        logger.debug("{}: Coordinator is ONLINE. Starting device initialisation.", nodeIeeeAddress);

        coordinatorHandler = (ZigBeeCoordinatorHandler) getBridge().getHandler();
        coordinatorHandler.addNetworkNodeListener(this);

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

    private void doNodeInitialisation() {
        if (nodeInitialised) {
            return;
        }

        logger.debug("{}: Initialising node", nodeIeeeAddress);

        // Load the node information
        ZigBeeNode node = coordinatorHandler.getNode(nodeIeeeAddress);
        if (node == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE,
                    getI18nConstant(ZigBeeBindingConstants.OFFLINE_NODE_NOT_FOUND));
            return;
        }

        // Create the channels from the device
        // Process all the endpoints for this device and add all channels as derived from the supported clusters
        List<Channel> clusterChannels = new ArrayList<Channel>();
        for (ZigBeeDevice device : coordinatorHandler.getNodeDevices(nodeIeeeAddress)) {
            clusterChannels.addAll(ZigBeeChannelConverter.getChannels(getThing().getUID(), device));
        }

        logger.debug("{}: Created {} channels", nodeIeeeAddress, clusterChannels.size());
        try {
            ThingBuilder thingBuilder = editThing();
            thingBuilder.withChannels(clusterChannels).withConfiguration(getConfig());
            updateThing(thingBuilder.build());
            // Create the channels list to simplify processing incoming events
            for (Channel channel : getThing().getChannels()) {
                // Process the channel properties
                Map<String, String> properties = channel.getProperties();

                ZigBeeChannelConverter handler = ZigBeeChannelConverter.getConverter(channel.getChannelTypeUID());
                if (handler == null) {
                    logger.debug("{}: No handler found for {}", nodeIeeeAddress, channel.getUID());
                    continue;
                }

                if (handler.createConverter(this, channel.getUID(), coordinatorHandler,
                        properties.get(ZigBeeBindingConstants.CHANNEL_PROPERTY_ADDRESS)) == false) {
                    logger.debug("{}: Initializing ZigBee thing handler", nodeIeeeAddress);
                    continue;
                }

                handler.initializeConverter();

                channels.put(channel.getUID(), handler);
            }
        } catch (Exception e) {
            logger.debug(e.toString());
        }

        logger.debug("{}: Initializing ZigBee thing handler", nodeIeeeAddress);
        updateNodeProperties(node);
        updateStatus(ThingStatus.ONLINE);

        nodeInitialised = true;

        return;
    }

    @Override
    public void dispose() {
        logger.debug("Handler disposes. Unregistering listener.");
        if (nodeIeeeAddress != null) {
            if (coordinatorHandler != null) {
                // coordinatorHandler.unsubscribeEvents(nodeAddress, this);
            }
            nodeIeeeAddress = null;
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("{}: Configuration received.", nodeIeeeAddress);

        // Sanity check
        if (configurationParameters == null) {
            logger.warn("{}: No configuration parameters provided.", nodeIeeeAddress);
            return;
        }

        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            switch (configurationParameter.getKey()) {
                case ZigBeeBindingConstants.CONFIGURATION_JOINENABLE:
                    coordinatorHandler.permitJoin(nodeIeeeAddress, 60);
                    break;
                case ZigBeeBindingConstants.CONFIGURATION_LEAVE:
                    coordinatorHandler.leave(nodeIeeeAddress);
                    break;
                default:
                    logger.warn("{}: Unhandled configuration parameter {}.", nodeIeeeAddress,
                            configurationParameter.getKey());
                    break;
            }
        }

        // Persist changes
        updateConfiguration(configuration);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("{}: Command for channel {} --> {}", nodeIeeeAddress, channelUID, command);

        // Check that we have a coordinator to work through
        if (coordinatorHandler == null) {
            logger.warn("Coordinator handler not found. Cannot handle command without coordinator.");
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        ZigBeeChannelConverter handler = channels.get(channelUID);
        if (handler == null) {
            logger.warn("No handler found for {}", channelUID);
            return;
        }

        // The handler returns a runnable so we schedule it for immediate execution
        Runnable commandHandler = handler.handleCommand(command);
        if (commandHandler != null) {
            scheduler.schedule(commandHandler, 0, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Callback from handlers to update a channel state
     *
     * @param channel
     * @param state
     */
    public void setChannelState(ChannelUID channel, State state) {
        updateState(channel, state);
        updateStatus(ThingStatus.ONLINE);
    }

    private void updateNodeProperties(final ZigBeeNode node) {
        final ZigBeeThingHandler thisHandler = this;
        Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                ZigBeeNodePropertyDiscoverer propertyDiscoverer = new ZigBeeNodePropertyDiscoverer();

                Map<String, String> newProperties = propertyDiscoverer.getProperties(coordinatorHandler, node);
                Map<String, String> orgProperties = thisHandler.editProperties();
                orgProperties.putAll(newProperties);
                thisHandler.updateProperties(orgProperties);
            }
        };

        scheduler.schedule(pollingRunnable, 10, TimeUnit.MILLISECONDS);
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

        // Update the network address
        networkAddress = node.getNetworkAddress();

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
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_PERMITJOINING, Boolean.toString(node.isJoiningEnabled()));
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_ASSOCIATEDDEVICES, node.getAssociatedDevices().toString());
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_LASTUPDATE,
                ZigBeeBindingConstants.getISO8601StringForDate(node.getLastUpdateTime()));
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_NETWORKADDRESS, node.getNetworkAddress().toString());

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
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_PERMITJOINING, "");
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_ROUTES, "[]");
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_NEIGHBORS, "[]");
        properties.put(ZigBeeBindingConstants.THING_PROPERTY_ASSOCIATEDDEVICES, "[]");
        updateProperties(properties);

        updateStatus(ThingStatus.OFFLINE);
    }

    public ZigBeeCoordinatorHandler getCoordinatorHandler() {
        return coordinatorHandler;
    }

    public IeeeAddress getIeeeAddress() {
        return nodeIeeeAddress;
    }

    // @SuppressWarnings("null")
    // public void commandReceived(ChannelUID channelUID, Command command) {
    // // Check if we received a ZCL command
    // if (command instanceof ZclCommand && ((ZclCommand) command).getCommandDirection() == true) {
    //
    // ZigBeeChannelConverter handler = channels.get(channelUID);
    // handler.handlePost((ZclCommand) command);
    // // Integer clusterIdNumber = ((ZclCommand) command).getClusterId();
    // // ClusterType clusterId = ClusterType.fromInteger(clusterIdNumber);
    // //
    // // // handle an unrecognized ZCL command
    // // if (clusterId == null) {
    // // logger.debug("Unknown command {}.", command.toString());
    // // }
    // //
    // // // find the appropriate ZCL command and forward it to an appropriate converter
    // // switch (clusterId) {
    // //
    // // case SWITCH_CLUSTER:
    // //
    // // break;
    // // case LEVEL_CONTROL_CLUSTER:
    // // break;
    // // case TEMPERATURE_MEASUREMENT_CLUSTER:
    // // break;
    // // case OCCUPANCY_SENSING_CLUSTER:
    // // break;
    // // case COLOR_CONTROL_CLUSTER:
    // // break;
    // // }
    // }
    // }
}
