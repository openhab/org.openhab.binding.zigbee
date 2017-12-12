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

import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;

/**
 * ZigBeeChannelFactory class provides a factory for creating channel converters for the ZigBee binding.
 * <p>
 * The factory performs two functions -
 * <ul>
 * <li>gets a list of channels the thing supports. This uses methods in each converter to decide if they are supported.
 * <li>instantiates converters based on the channel UID.
 * </ul>
 *
 * @author Chris Jackson
 */
public class ZigBeeChannelConverterFactory {
    private static Logger logger = LoggerFactory.getLogger(ZigBeeChannelConverterFactory.class);

    /**
     * Map of all channels supported by the binding
     */
    private final Map<String, Class<? extends ZigBeeBaseChannelConverter>> channelMap;

    public ZigBeeChannelConverterFactory() {
        channelMap = new HashMap<String, Class<? extends ZigBeeBaseChannelConverter>>();

        // Add all the converters into the map...
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_COLOR, ZigBeeConverterColorColor.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_TEMPERATURE, ZigBeeConverterColorTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_CONTACT_PORTAL1, ZigBeeConverterIasContactPortal1.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_MOTION_INTRUSION, ZigBeeConverterIasMotionIntrusion.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_MOTION_PRESENCE, ZigBeeConverterIasMotionPresence.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ILLUMINANCE_VALUE, ZigBeeConverterIlluminance.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_OCCUPANCY_SENSOR, ZigBeeConverterOccupancy.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_POWER_BATTERYPERCENT, ZigBeeConverterBatteryPercent.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF, ZigBeeConverterSwitchOnoff.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL, ZigBeeConverterSwitchLevel.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE, ZigBeeConverterTemperature.class);
    }

    /**
     * Gets a list of all channels supported by the {@link ZigBeeEndpoint}
     *
     * @param thingUID the {@link ThingUID} of the thing
     * @param endpoint the {@link ZigBeeEndpoint} to generate the channels for
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Channel> getChannels(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        List<Channel> channels = new ArrayList<Channel>();

        Constructor<? extends ZigBeeBaseChannelConverter> constructor;
        for (Class<?> converterClass : channelMap.values()) {
            try {
                constructor = (Constructor<? extends ZigBeeBaseChannelConverter>) converterClass.getConstructor();
                ZigBeeBaseChannelConverter converter = constructor.newInstance();

                Channel channel = converter.getChannel(thingUID, endpoint);
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
     * Creates a channel converter for the requested {@link ChannelTypeUID}
     *
     * @param thingHandler the {@link ZigBeeThingHandler} for this channel
     * @param channelTypeUid the {@link ChannelTypeUID} to create the converter for
     * @param channelUid the {@link ChannelUID} to create the converter for
     * @param coordinatorHandler the {@link ZigBeeCoordinatorHandler}
     * @param ieeeAddress the {@link IeeeAddress} of the device
     * @param endpointId the endpoint ID for this channel on the device
     * @return the {@link ZigBeeBaseChannelConverter} or null if the channel is not supported
     */
    public ZigBeeBaseChannelConverter createConverter(ZigBeeThingHandler thingHandler, ChannelTypeUID channelTypeUid,
            ChannelUID channelUid, ZigBeeCoordinatorHandler coordinatorHandler, IeeeAddress ieeeAddress,
            int endpointId) {
        Constructor<? extends ZigBeeBaseChannelConverter> constructor;
        try {
            if (channelMap.get(channelTypeUid.getId()) == null) {
                logger.debug("Channel converter for channel type {} is not implemented!", channelUid.getId());
                return null;
            }
            constructor = channelMap.get(channelTypeUid.getId()).getConstructor();
            ZigBeeBaseChannelConverter instance = constructor.newInstance();

            instance.initialize(thingHandler, channelUid, coordinatorHandler, ieeeAddress, endpointId);
            return instance;
        } catch (Exception e) {
            logger.debug("Unable to create channel " + channelUid, e);
        }

        return null;
    }
}
