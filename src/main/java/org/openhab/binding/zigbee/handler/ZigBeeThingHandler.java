/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
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
import org.openhab.binding.zigbee.discovery.ZigBeeNodePropertyDiscoverer;
import org.openhab.binding.zigbee.handler.cluster.ZigBeeClusterHandler;
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
    private HashMap<ChannelUID, ZigBeeClusterHandler> channels = new HashMap<ChannelUID, ZigBeeClusterHandler>();

    private IeeeAddress nodeIeeeAddress = null;

    private Logger logger = LoggerFactory.getLogger(ZigBeeThingHandler.class);

    private ZigBeeCoordinatorHandler coordinatorHandler;

    private ScheduledFuture<?> pollingJob;

    public ZigBeeThingHandler(Thing zigbeeDevice) {
        super(zigbeeDevice);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee thing handler {}.", getThing().getUID());
        final String configAddress = (String) getConfig().get(ZigBeeBindingConstants.THING_PARAMETER_MACADDRESS);

        if (configAddress == null || configAddress.length() == 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    ZigBeeBindingConstants.getI18nConstant(ZigBeeBindingConstants.OFFLINE_NO_ADDRESS));
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
        logger.debug("{}: Initialising node", nodeIeeeAddress);

        // Load the node information
        ZigBeeNode node = coordinatorHandler.getNode(nodeIeeeAddress);
        if (node == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE,
                    ZigBeeBindingConstants.getI18nConstant(ZigBeeBindingConstants.OFFLINE_NODE_NOT_FOUND));
            return;
        }

        // Create the channels from the device
        // Process all the endpoints for this device and add all channels as derived from the supported clusters
        List<Channel> clusterChannels = new ArrayList<Channel>();
        for (ZigBeeDevice device : coordinatorHandler.getNodeDevices(nodeIeeeAddress)) {
            for (int cluster : device.getInputClusterIds()) {
                ZigBeeClusterHandler handler = ZigBeeClusterHandler.getConverter(cluster);
                if (handler != null) {
                    clusterChannels.addAll(handler.getChannels(getThing().getUID(), device));
                }
            }
        }

        ThingBuilder thingBuilder = editThing();
        thingBuilder.withChannels(clusterChannels).withConfiguration(getConfig());
        updateThing(thingBuilder.build());
        // Create the channels list to simplify processing incoming events
        for (Channel channel : getThing().getChannels()) {
            // Process the channel properties
            Map<String, String> properties = channel.getProperties();

            String strCluster = properties.get(ZigBeeBindingConstants.CHANNEL_PROPERTY_CLUSTER);
            if (strCluster == null) {
                logger.debug("{}: No cluster set for {}", nodeIeeeAddress, channel.getUID());
                continue;
            }
            Integer intCluster = Integer.parseInt(strCluster);

            ZigBeeClusterHandler handler = ZigBeeClusterHandler.getConverter(intCluster);
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

        logger.debug("{}: Initializing ZigBee thing handler", nodeIeeeAddress);
        updateNodeProperties(node);

        updateStatus(ThingStatus.ONLINE);

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
        // Sanity check
        if (configurationParameters == null) {
            logger.warn("{}: No configuration parameters provided.", nodeIeeeAddress);
            return;
        }

        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            String[] cfg = configurationParameter.getKey().split("_");
            if ("config".equals(cfg[0])) {
                if (cfg.length != 4) {
                    logger.warn("{}: Configuration invalid {}", nodeIeeeAddress, configurationParameter.getKey());
                    continue;
                }

                int endpoint = Integer.parseInt(cfg[1]);
                int cluster = Integer.parseInt(cfg[2]);
                String address = nodeIeeeAddress + "/" + endpoint;

                // final ZigBeeDevice device = coordinatorHandler.getDevice(address);
                // try {
                // coordinatorHandler.bind(address, cluster);
                // logger.warn("{}: Error adding binding for cluster {}", address, cluster);
                // } catch (ZigBeeApiException e) {
                // TODO Auto-generated catch block
                // e.printStackTrace();
                // }
            } else {
                logger.warn("{}: Configuration invalid {}", nodeIeeeAddress, configurationParameter.getKey());
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

        ZigBeeClusterHandler handler = channels.get(channelUID);
        if (handler == null) {
            logger.warn("No handler found for {}", channelUID);
            return;
        }

        scheduler.schedule(handler.handleCommand(command), 0, TimeUnit.MILLISECONDS);
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

        Map<String, String> orgProperties = editProperties();

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
            object.put("depth", neighbor.getDepth());
            object.put("lqi", neighbor.getLqi());
            jsonBuilder.append(ZigBeeBindingConstants.propertiesToJson(object));
        }
        jsonBuilder.append("]");
        orgProperties.put(ZigBeeBindingConstants.THING_PROPERTY_NEIGHBORS, jsonBuilder.toString());

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
        orgProperties.put(ZigBeeBindingConstants.THING_PROPERTY_ROUTES, jsonBuilder.toString());

        orgProperties.put(ZigBeeBindingConstants.THING_PROPERTY_LASTUPDATE,
                ZigBeeBindingConstants.getISO8601StringForDate(node.getLastUpdateTime()));

        updateProperties(orgProperties);

        initialiseZigBeeNode();
    }

    @Override
    public void nodeRemoved(ZigBeeNode node) {
        // Make sure it's our node that's updated
        if (!node.getIeeeAddress().equals(nodeIeeeAddress)) {
            return;
        }

        updateStatus(ThingStatus.REMOVED);
    }
}
