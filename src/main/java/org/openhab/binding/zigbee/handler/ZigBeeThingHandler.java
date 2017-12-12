/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.ConfigDescription;
import org.eclipse.smarthome.config.core.ConfigDescriptionProvider;
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
import org.eclipse.smarthome.core.thing.binding.firmware.Firmware;
import org.eclipse.smarthome.core.thing.binding.firmware.FirmwareUpdateHandler;
import org.eclipse.smarthome.core.thing.binding.firmware.ProgressCallback;
import org.eclipse.smarthome.core.thing.binding.firmware.ProgressStep;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.discovery.ZigBeeNodePropertyDiscoverer;
import org.openhab.binding.zigbee.internal.ZigBeeActivator;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaFile;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaServer;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaServerStatus;
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaStatusCallback;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster;
import com.zsmartsystems.zigbee.zdo.field.NeighborTable;
import com.zsmartsystems.zigbee.zdo.field.RoutingTable;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeThingHandler extends BaseThingHandler
        implements ZigBeeNetworkNodeListener, FirmwareUpdateHandler, ConfigDescriptionProvider {
    /**
     * Our logger
     */
    private Logger logger = LoggerFactory.getLogger(ZigBeeThingHandler.class);

    private Map<ChannelUID, ZigBeeBaseChannelConverter> channels = new HashMap<ChannelUID, ZigBeeBaseChannelConverter>();

    private IeeeAddress nodeIeeeAddress = null;

    private ZigBeeCoordinatorHandler coordinatorHandler;

    private boolean nodeInitialised = false;

    private final TranslationProvider translationProvider;

    private ServiceRegistration<FirmwareUpdateHandler> firmwareRegistration;

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
        final String configAddress = (String) getConfig().get(ZigBeeBindingConstants.CONFIGURATION_MACADDRESS);
        logger.debug("Initializing ZigBee thing handler {}, Ieee=\"{}\".", getThing().getUID(), configAddress);

        if (configAddress == null || configAddress.length() == 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    getI18nConstant(ZigBeeBindingConstants.OFFLINE_NO_ADDRESS));
            return;
        }
        nodeIeeeAddress = new IeeeAddress(configAddress);

        updateStatus(ThingStatus.UNKNOWN);

        if (getBridge() != null) {
            bridgeStatusChanged(getBridge().getStatusInfo());
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("{}: Coordinator status changed to {}.", nodeIeeeAddress, bridgeStatusInfo.getStatus());

        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE || getBridge() == null) {
            logger.debug("{}: Coordinator is unknown or not online.", nodeIeeeAddress, bridgeStatusInfo.getStatus());
            return;
        }

        logger.debug("{}: Coordinator is ONLINE. Starting device initialisation.", nodeIeeeAddress);

        coordinatorHandler = (ZigBeeCoordinatorHandler) getBridge().getHandler();
        coordinatorHandler.addNetworkNodeListener(this);
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

    @SuppressWarnings("unchecked")
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

        // Create the channel factory
        ZigBeeChannelConverterFactory factory = new ZigBeeChannelConverterFactory();

        // Create the channels from the device
        // Process all the endpoints for this device and add all channels as derived from the supported clusters
        List<Channel> nodeChannels = new ArrayList<Channel>();
        for (ZigBeeEndpoint endpoint : coordinatorHandler.getNodeEndpoints(nodeIeeeAddress)) {
            nodeChannels.addAll(factory.getChannels(getThing().getUID(), endpoint));
        }

        // TODO: Perform a channel consolidation to remove unnecessary channels.
        // This removes channels that a re covered through inheritance.

        logger.debug("{}: Created {} channels", nodeIeeeAddress, nodeChannels.size());
        try {
            ThingBuilder thingBuilder = editThing();
            thingBuilder.withChannels(nodeChannels).withConfiguration(getConfig());
            updateThing(thingBuilder.build());

            // Create the channel map to simplify processing incoming events
            for (Channel channel : getThing().getChannels()) {
                // Process the channel properties
                Map<String, String> properties = channel.getProperties();

                ZigBeeBaseChannelConverter handler = factory.createConverter(this, channel.getChannelTypeUID(),
                        channel.getUID(), coordinatorHandler, node.getIeeeAddress(),
                        Integer.parseInt(properties.get(ZigBeeBindingConstants.CHANNEL_PROPERTY_ENDPOINT)));
                if (handler == null) {
                    logger.debug("{}: No handler found for {}", nodeIeeeAddress, channel.getUID());
                    continue;
                }

                handler.initializeConverter();

                // TODO: Update the channel configuration from the device if method available
                handler.updateConfiguration(channel.getConfiguration());

                channels.put(channel.getUID(), handler);
            }
        } catch (Exception e) {
            logger.debug("Exception creating channels for " + nodeIeeeAddress + ": ", e);
        }

        logger.debug("{}: Initializing ZigBee thing handler", nodeIeeeAddress);
        updateNodeProperties(node);
        updateStatus(ThingStatus.ONLINE);

        nodeInitialised = true;
    }

    @Override
    public void dispose() {
        if (firmwareRegistration != null) {
            firmwareRegistration.unregister();
        }

        logger.debug("Handler dispose. Unregistering listener.");
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
    public void handleCommand(final ChannelUID channelUID, final Command command) {
        logger.debug("{}: Command for channel {} --> {}", nodeIeeeAddress, channelUID, command);

        // Check that we have a coordinator to work through
        if (coordinatorHandler == null) {
            logger.debug("Coordinator handler not found. Cannot handle command without coordinator.");
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        ZigBeeBaseChannelConverter handler = channels.get(channelUID);
        if (handler == null) {
            logger.debug("No handler found for {}", channelUID);
            return;
        }

        Runnable commandHandler = new Runnable() {
            @Override
            public void run() {
                if (command == RefreshType.REFRESH) {
                } else {
                    handler.handleCommand(command);
                }
            }
        };
        scheduler.schedule(commandHandler, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Callback from handlers to update a channel state. This is called from the channel converter when the state
     * changes.
     *
     * @param channel the {@link ChannelUID} to be updated
     * @param state the new {link State}
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

    @Override
    public Collection<ConfigDescription> getConfigDescriptions(Locale locale) {
        return Collections.emptySet();
    }

    @Override
    public ConfigDescription getConfigDescription(URI uri, Locale locale) {
        if (uri == null) {
            return null;
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

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.FIRMWARE_UPDATING);

        // Define the sequence of the firmware update so that external consumers can listen for the progress
        progressCallback.defineSequence(ProgressStep.DOWNLOADING, ProgressStep.TRANSFERRING, ProgressStep.UPDATING);

        // Find an OTA client if the device supports OTA upgrades
        ZigBeeNode node = coordinatorHandler.getNode(nodeIeeeAddress);
        if (node == null) {
            logger.debug("{}: Can't find node", nodeIeeeAddress);
            return;
        }

        ZigBeeOtaServer otaServer = null;
        ZigBeeEndpoint otaEndpoint = null;
        ZclOtaUpgradeCluster otaCluster = null;
        for (ZigBeeEndpoint endpoint : node.getEndpoints()) {
            otaServer = (ZigBeeOtaServer) endpoint.getExtension(ZclOtaUpgradeCluster.CLUSTER_ID);
            if (otaServer != null) {
                break;
            }

            otaCluster = (ZclOtaUpgradeCluster) endpoint.getOutputCluster(ZclOtaUpgradeCluster.CLUSTER_ID);
            if (otaCluster != null) {
                otaEndpoint = endpoint;
                break;
            }
        }

        if (otaServer == null && otaCluster == null) {
            logger.debug("{}: Can't find OTA cluster", nodeIeeeAddress);
            return;
        }

        // Register the OTA server if it's not already registered
        if (otaServer == null && otaEndpoint != null) {
            otaServer = new ZigBeeOtaServer();
            otaEndpoint.addExtension(otaServer);
        } else {
            logger.debug("{}: Can't create OTA server", nodeIeeeAddress);
            return;
        }

        ZigBeeOtaFile otaFile = new ZigBeeOtaFile(firmware.getBytes());
        otaServer.setFirmware(otaFile);

        // DOWNLOADING
        progressCallback.next();

        final ZigBeeOtaServer finalOtaServer = otaServer;
        otaServer.addListener(new ZigBeeOtaStatusCallback() {
            @Override
            public void otaStatusUpdate(ZigBeeOtaServerStatus status) {
                switch (status) {
                    case OTA_WAITING:
                        // progressCallback.next();
                        return;
                    case OTA_TRANSFER_IN_PROGRESS:
                        // TRANSFERRING
                        progressCallback.next();
                        return;
                    case OTA_TRANSFER_COMPLETE:
                        // UPDATING
                        progressCallback.next();
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
                finalOtaServer.cancelUpgrade();
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
