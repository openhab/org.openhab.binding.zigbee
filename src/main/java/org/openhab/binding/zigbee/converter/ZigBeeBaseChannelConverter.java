/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.config.core.ConfigDescription;
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
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_MODE, ZigBeeConverterColorMode.class);
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
     * If the converter doesn't support any features, it returns null
     *
     * @param thingUID the {@link ThingUID} of the thing to which the channel will be attached
     * @param endpoint The {@link ZigBeeEndpoint} to search for channels
     * @return a {@link Channel} if the converter supports fetures from the {@link ZigBeeEndpoint}, otherwise null.
     */
    public abstract Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint);

    /**
     *
     * @param clusterId
     * @return
     */
    public static ZigBeeBaseChannelConverter getConverter(ChannelTypeUID channelTypeUID) {

        Constructor<? extends ZigBeeBaseChannelConverter> constructor;
        try {
            if (channelMap.get(channelTypeUID.getId()) == null) {
                logger.debug("Channel converter for channel type {} is not implemented!", channelTypeUID.getId());
                return null;
            }
            constructor = channelMap.get(channelTypeUID.getId()).getConstructor();
            return constructor.newInstance();
        } catch (Exception e) {
            // logger.error("Command processor error");
        }

        return null;
    }

    protected void updateChannelState(State state) {
        thing.setChannelState(channelUID, state);
    }

    /**
     * Gets the configuration descriptions required for this cluster
     *
     * @return {@link ConfigDescription} null if no config is provided
     */
    public ConfigDescription getConfigDescription() {
        return null;
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
}
