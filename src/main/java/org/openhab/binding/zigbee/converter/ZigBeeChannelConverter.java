/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress;

/**
 * ZigBeeChannelConverter class. Base class for all converters that convert between ZigBee clusters and openHAB
 * channels.
 *
 * @author Chris Jackson
 */
public abstract class ZigBeeChannelConverter {
    private static Logger logger = LoggerFactory.getLogger(ZigBeeChannelConverter.class);

    protected ZigBeeThingHandler thing = null;
    protected ZigBeeCoordinatorHandler coordinator = null;

    protected ChannelUID channelUID = null;
    protected ZigBeeEndpoint device = null;

    /**
     * Map of all channels supported by the binding
     */
    private static Map<String, Class<? extends ZigBeeChannelConverter>> channelMap = null;

    static {
        channelMap = new HashMap<String, Class<? extends ZigBeeChannelConverter>>();

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
     * Constructor. Creates a new instance of the {@link ZigBeeChannelConverter} class.
     *
     */
    public ZigBeeChannelConverter() {
        super();
    }

    /**
     * Creates the converter handler
     *
     * @param thing the {@link ZigBeeThingHandler} the channel is part of
     * @param channelUID the {@link channelUID} for the channel
     * @param coordinator the {@link ZigBeeCoordinatorHandler} this node is part of
     * @param address
     * @return true if the handler was created successfully - false otherwise
     */
    public boolean createConverter(ZigBeeThingHandler thing, ChannelUID channelUID,
            ZigBeeCoordinatorHandler coordinator, String address) {
        this.device = coordinator.getEndpoint(new ZigBeeEndpointAddress(address));
        if (this.device == null) {
            return false;
        }
        this.thing = thing;
        this.channelUID = channelUID;
        this.coordinator = coordinator;

        return true;
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
    }

    /**
     * Execute refresh method. This method is called every time a binding item is refreshed and the corresponding node
     * should be sent a message.
     *
     * @param channel the {@link ZigBeeThingChannel}
     */
    public void handleRefresh() {
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

    public abstract Channel getChannel(ThingUID thingUID, ZigBeeEndpoint device);

    @SuppressWarnings("unchecked")
    public static List<Channel> getChannels(ThingUID thingUID, ZigBeeEndpoint device) {
        List<Channel> channels = new ArrayList<Channel>();

        Constructor<? extends ZigBeeChannelConverter> constructor;
        for (Class<?> converterClass : channelMap.values()) {
            try {
                constructor = (Constructor<? extends ZigBeeChannelConverter>) converterClass.getConstructor();
                ZigBeeChannelConverter converter = constructor.newInstance();

                Channel channel = converter.getChannel(thingUID, device);
                if (channel != null) {
                    channels.add(channel);
                }
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                logger.debug("Exception while getting channels: ", e);
            }
        }
        return channels;
    }

    /**
     *
     * @param clusterId
     * @return
     */
    public static ZigBeeChannelConverter getConverter(ChannelTypeUID channelTypeUID) {

        Constructor<? extends ZigBeeChannelConverter> constructor;
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

    protected Channel createChannel(ZigBeeEndpoint device, ThingUID thingUID, String channelType, String itemType,
            String label) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(ZigBeeBindingConstants.CHANNEL_PROPERTY_ADDRESS, device.getEndpointAddress().toString());
        // properties.put(ZigBeeBindingConstants.CHANNEL_PROPERTY_CLUSTER, Integer.toString(getClusterId()));
        ChannelTypeUID channelTypeUID = new ChannelTypeUID(ZigBeeBindingConstants.BINDING_ID, channelType);

        return ChannelBuilder
                .create(new ChannelUID(thingUID,
                        device.getIeeeAddress() + "_" + device.getEndpointId() + "_" + channelType), itemType)
                .withType(channelTypeUID).withLabel(label).withProperties(properties).build();
    }
}
