/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
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

import com.zsmartsystems.zigbee.ZigBeeDevice;
import com.zsmartsystems.zigbee.ZigBeeDeviceAddress;

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
    protected ZigBeeDevice device = null;

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
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_TEMPERATURE, ZigBeeConverterColorTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE, ZigBeeConverterTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_OCCUPANCY, ZigBeeConverterOccupancySensor.class);
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
     * @param thing
     * @param channelUID
     * @param coordinator
     * @param address
     * @return true if the handler was created successfully - false otherwise
     */
    public boolean createConverter(ZigBeeThingHandler thing, ChannelUID channelUID,
            ZigBeeCoordinatorHandler coordinator, String address) {
        this.device = coordinator.getDevice(new ZigBeeDeviceAddress(address));
        if (this.device == null) {
            return false;
        }
        this.thing = thing;
        this.channelUID = channelUID;
        this.coordinator = coordinator;

        return true;
    }

    public abstract void initializeConverter();

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

    public abstract Channel getChannel(ThingUID thingUID, ZigBeeDevice device);
    
    public static List<Channel> getChannels(ThingUID thingUID, ZigBeeDevice device) {
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
                logger.error("Exception while getting channels: ", e.toString());
                e.printStackTrace();
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
             logger.error("Command processor error {}", e.toString()); 
             e.printStackTrace();
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

    protected Channel createChannel(ZigBeeDevice device, ThingUID thingUID, String channelType, String itemType,
            String label) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(ZigBeeBindingConstants.CHANNEL_PROPERTY_ADDRESS, device.getDeviceAddress().toString());
              ChannelTypeUID channelTypeUID = new ChannelTypeUID(ZigBeeBindingConstants.BINDING_ID, channelType);

        return ChannelBuilder
                .create(new ChannelUID(thingUID,
                        device.getIeeeAddress() + "_" + device.getEndpoint() + "_" + channelType), itemType)
                .withType(channelTypeUID).withLabel(label).withProperties(properties).build();
    }
}