/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclCluster;

/**
 * ZigBeeChannelConverter class. Base class for all converters that convert between ZigBee clusters and openHAB
 * channels.
 *
 * @author Chris Jackson
 */
public abstract class ZigBeeBaseChannelConverter {
    private static Logger logger = LoggerFactory.getLogger(ZigBeeBaseChannelConverter.class);

    protected ZigBeeThingHandler thing = null;
    protected ZigBeeCoordinatorHandler coordinator = null;

    protected List<ConfigDescriptionParameter> configOptions = null;

    protected ChannelUID channelUID = null;
    protected ZigBeeEndpoint endpoint = null;

    /**
     * Map of all channels supported by the binding
     */
    private static Map<String, Class<? extends ZigBeeBaseChannelConverter>> channelMap = null;

    static {
        channelMap = new HashMap<String, Class<? extends ZigBeeBaseChannelConverter>>();

        // Add all the converters into the map...
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF, ZigBeeConverterSwitchOnoff.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL, ZigBeeConverterSwitchLevel.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_COLOR, ZigBeeConverterColorColor.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_TEMPERATURE, ZigBeeConverterColorTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE, ZigBeeConverterTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_OCCUPANCY_SENSOR, ZigBeeConverterOccupancy.class);
    }

    /**
     * Constructor. Creates a new instance of the {@link ZigBeeBaseChannelConverter} class.
     *
     */
    public ZigBeeBaseChannelConverter() {
        super();
    }

    /**
     * Creates the converter handler
     *
     * @param thing the {@link ZigBeeThingHandler} the channel is part of
     * @param channelUID the {@link channelUID} for the channel
     * @param coordinator the {@link ZigBeeCoordinatorHandler} this node is part of
     * @param address the {@link IeeeAddress} of the node
     * @param endpointId the endpoint this channel is linked to
     * @return true if the handler was created successfully - false otherwise
     */
    public void initialize(ZigBeeThingHandler thing, ChannelUID channelUID, ZigBeeCoordinatorHandler coordinator,
            IeeeAddress address, int endpointId) {
        this.endpoint = coordinator.getEndpoint(address, endpointId);
        if (this.endpoint == null) {
            throw new IllegalArgumentException("Device was not found");
        }
        this.thing = thing;
        this.channelUID = channelUID;
        this.coordinator = coordinator;
    }

    /**
     * Initialise the converter. This is called by the {@link ZigBeeThingHandler} when the channel is created. The
     * converter should initialise any internal states, open any clusters, add reporting and binding that it needs to
     * operate.
     * A list of configuration parameters for the thing should be built based on the features the device supports
     */
    public abstract void initializeConverter();

    /**
     * Closes the converter and releases any resources.
     */
    public void disposeConverter() {
        // Overridable if the converter has cleanup to perform
    }

    /**
     * Execute refresh method. This method is called every time a binding item is refreshed and the corresponding node
     * should be sent a message.
     *
     * @param channel the {@link ZigBeeThingChannel}
     */
    public void handleRefresh() {
        // Overridable if a channel can be refreshed
    }

    /**
     * Receives a command from openHAB and translates it to an operation on the ZigBeee network.
     *
     * @param channel the {@link ZigBeeThingChannel}
     * @param command the {@link Command} to send
     */
    public Runnable handleCommand(final Command command) {
        return null;
    }

    /**
     * Creates a {@link Channel} if this converter supports features from the {@link ZigBeeEndpoint}
     * If the converter doesn't support any features, it returns null.
     * <p>
     * The converter should perform the following -:
     * <ul>
     * <li>Check if the device supports the cluster(s) required by the converter
     * <li>Check if the cluster supports the attributes or commands required by the converter
     * </ul>
     * Only if the device supports the features required by the channel should the channel be implemented.
     *
     * @param thingUID the {@link ThingUID} of the thing to which the channel will be attached
     * @param endpoint The {@link ZigBeeEndpoint} to search for channels
     * @return a {@link Channel} if the converter supports fetures from the {@link ZigBeeEndpoint}, otherwise null.
     */
    public abstract Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint);

    /**
     * Updates the channel state within the thing.
     *
     * @param state the updated {@link State}
     */
    protected void updateChannelState(State state) {
        thing.setChannelState(channelUID, state);
    }

    /**
     * Gets the configuration descriptions required to configure this channel.
     * <p>
     * Ideally, implementations should use the {@link ZclCluster#discoverAttributes()} method to understand exactly what
     * the device supports and only provide configuration as necessary.
     *
     * @return a {@link List} of {@link ConfigDescriptionParameter}s. null if no config is provided
     */
    public List<ConfigDescriptionParameter> getConfigDescription() {
        return configOptions;
    }

    /**
     * Creates a channel. This is called from extended converters to create a channel they support
     *
     * @param thingUID the {@link ThingUID}
     * @param channelType the channel uid as a string
     * @param itemType the item type for the channel
     * @param label the label for the channel
     * @return
     */
    protected Channel createChannel(ThingUID thingUID, ZigBeeEndpoint endpoint, String channelType, String itemType,
            String label) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(ZigBeeBindingConstants.CHANNEL_PROPERTY_ENDPOINT, Integer.toString(endpoint.getEndpointId()));
        ChannelTypeUID channelTypeUID = new ChannelTypeUID(ZigBeeBindingConstants.BINDING_ID, channelType);

        return ChannelBuilder
                .create(new ChannelUID(thingUID,
                        endpoint.getIeeeAddress() + "_" + endpoint.getEndpointId() + "_" + channelType), itemType)
                .withType(channelTypeUID).withLabel(label).withProperties(properties).build();
    }

    /**
     * Update the channel configuration
     *
     * @param configuration
     */
    public void updateConfiguration(@NonNull Configuration configuration) {
        // Nothing required as default implementation
    }
}
