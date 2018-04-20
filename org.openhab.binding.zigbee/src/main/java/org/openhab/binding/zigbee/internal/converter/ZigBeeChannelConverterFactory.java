/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.smarthome.core.thing.Channel;
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
    private Logger logger = LoggerFactory.getLogger(ZigBeeChannelConverterFactory.class);

    /**
     * Map of all channels supported by the binding
     */
    private final Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> channelMap;

    /**
     * Map of all channels to be consolidated. Note that order is important.
     */
    private final Map<ChannelTypeUID, ChannelTypeUID> channelConsolidation;

    public ZigBeeChannelConverterFactory() {
        channelMap = new HashMap<>();

        // Add all the converters into the map...
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_COLOR, ZigBeeConverterColorColor.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_TEMPERATURE, ZigBeeConverterColorTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ELECTRICAL_ACTIVEPOWER, ZigBeeConverterMeasurementPower.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_HUMIDITY_VALUE, ZigBeeConverterRelativeHumidity.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_CONTACTPORTAL1, ZigBeeConverterIasContactPortal1.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_MOTIONINTRUSION, ZigBeeConverterIasMotionIntrusion.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_MOTIONPRESENCE, ZigBeeConverterIasMotionPresence.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_STANDARDCIESYSTEM, ZigBeeConverterIasCieSystem.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ILLUMINANCE_VALUE, ZigBeeConverterIlluminance.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_OCCUPANCY_SENSOR, ZigBeeConverterOccupancy.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_POWER_BATTERYPERCENT, ZigBeeConverterBatteryPercent.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_POWER_BATTERYVOLTAGE, ZigBeeConverterBatteryVoltage.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_PRESSURE_VALUE, ZigBeeConverterAtmosphericPressure.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF, ZigBeeConverterSwitchOnoff.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL, ZigBeeConverterSwitchLevel.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE, ZigBeeConverterTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ELECTRICAL_RMSVOLTAGE,
                ZigBeeConverterMeasurementRmsVoltage.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ELECTRICAL_RMSCURRENT,
                ZigBeeConverterMeasurementRmsCurrent.class);

        // Add the hierarchical list of channels that are to be removed due to inheritance
        // Note that order is important in the event that there are multiple removals...
        // eg we want to remove switch before dimmer, then dimmer if color is present
        // If device creates both channels, then we keep the one on the right (ie map.value).
        channelConsolidation = new LinkedHashMap<>();

        // Remove ON/OFF if we support LEVEL
        channelConsolidation.put(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL,
                ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF);
        // Remove LEVEL if we support COLOR
        channelConsolidation.put(ZigBeeBindingConstants.CHANNEL_COLOR_COLOR,
                ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL);
    }

    /**
     * Gets a list of all channels supported by the {@link ZigBeeEndpoint}
     *
     * @param thingUID the {@link ThingUID} of the thing
     * @param endpoint the {@link ZigBeeEndpoint} to generate the channels for
     * @return
     */
    @SuppressWarnings("unchecked")
    public Collection<Channel> getChannels(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        Map<String, Channel> channels = new HashMap<String, Channel>();

        Constructor<? extends ZigBeeBaseChannelConverter> constructor;
        for (Class<?> converterClass : channelMap.values()) {
            try {
                constructor = (Constructor<? extends ZigBeeBaseChannelConverter>) converterClass.getConstructor();
                ZigBeeBaseChannelConverter converter = constructor.newInstance();

                Channel channel = converter.getChannel(thingUID, endpoint);
                if (channel != null) {
                    channels.put(channel.getChannelTypeUID().toString(), channel);
                }
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                logger.debug("{}: Exception while getting channels: ", endpoint.getIeeeAddress(), e);
            }
        }

        // Perform a channel consolidation at endpoint level to remove unnecessary channels.
        // This removes channels that are covered through inheritance.
        for (Map.Entry<ChannelTypeUID, ChannelTypeUID> consolidationChannel : channelConsolidation.entrySet()) {
            if (channels.containsKey(consolidationChannel.getKey())
                    && channels.containsKey(consolidationChannel.getValue())) {
                logger.debug("{}: Removing channel {} in favor of {}", endpoint.getIeeeAddress(),
                        consolidationChannel.getValue(), consolidationChannel.getKey());
                channels.remove(consolidationChannel.getValue());
            }
        }
        return channels.values();
    }

    /**
     * Creates a channel converter for the requested {@link ChannelTypeUID}
     *
     * @param thingHandler the {@link ZigBeeThingHandler} for this channel
     * @param channel the {@link Channel} to create the converter for
     * @param coordinatorHandler the {@link ZigBeeCoordinatorHandler}
     * @param ieeeAddress the {@link IeeeAddress} of the device
     * @param endpointId the endpoint ID for this channel on the device
     * @return the {@link ZigBeeBaseChannelConverter} or null if the channel is not supported
     */
    public ZigBeeBaseChannelConverter createConverter(ZigBeeThingHandler thingHandler, Channel channel,
            ZigBeeCoordinatorHandler coordinatorHandler, IeeeAddress ieeeAddress, int endpointId) {
        Constructor<? extends ZigBeeBaseChannelConverter> constructor;
        try {
            if (channelMap.get(channel.getChannelTypeUID()) == null) {
                logger.debug("{}: Channel converter for channel type {} is not implemented!", ieeeAddress,
                        channel.getUID().getId());
                return null;
            }
            constructor = channelMap.get(channel.getChannelTypeUID().toString()).getConstructor();
            ZigBeeBaseChannelConverter instance = constructor.newInstance();

            instance.initialize(thingHandler, channel, coordinatorHandler, ieeeAddress, endpointId);
            return instance;
        } catch (Exception e) {
            logger.error("{}: Unable to create channel {}", ieeeAddress, channel.getUID(), e);
        }

        return null;
    }
}
