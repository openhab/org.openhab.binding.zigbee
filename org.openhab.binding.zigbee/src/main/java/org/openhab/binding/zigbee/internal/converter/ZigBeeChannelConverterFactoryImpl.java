/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.internal.converter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterProvider;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;

@Component(immediate = true)
public final class ZigBeeChannelConverterFactoryImpl implements ZigBeeChannelConverterFactory {

    private final Logger logger = LoggerFactory.getLogger(ZigBeeChannelConverterFactoryImpl.class);

    /**
     * Map of all channels supported by the binding
     */
    private final Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> channelMap = new HashMap<>();

    /**
     * Map of all channels to be consolidated. Note that order is important.
     */
    private final Map<ChannelTypeUID, ChannelTypeUID> channelConsolidation = new LinkedHashMap<>();

    public ZigBeeChannelConverterFactoryImpl() {
        // Add the hierarchical list of channels that are to be removed due to inheritance
        // Note that order is important in the event that there are multiple removals...
        // eg we want to remove switch before dimmer, then dimmer if color is present
        // If device creates both channels, then we keep the one on the right (ie map.value).

        // Remove ON/OFF if we support LEVEL
        channelConsolidation.put(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL,
                ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF);
        // Remove LEVEL if we support COLOR
        channelConsolidation.put(ZigBeeBindingConstants.CHANNEL_COLOR_COLOR,
                ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<Channel> getChannels(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        Map<ChannelTypeUID, Channel> channels = new HashMap<>();

        Constructor<? extends ZigBeeBaseChannelConverter> constructor;
        for (Class<?> converterClass : channelMap.values()) {
            try {
                constructor = (Constructor<? extends ZigBeeBaseChannelConverter>) converterClass.getConstructor();
                ZigBeeBaseChannelConverter converter = constructor.newInstance();

                Channel channel = converter.getChannel(thingUID, endpoint);
                if (channel != null) {
                    channels.put(channel.getChannelTypeUID(), channel);
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

    @Override
    public ZigBeeBaseChannelConverter createConverter(Channel channel, ZigBeeCoordinatorHandler coordinatorHandler,
            IeeeAddress ieeeAddress, int endpointId) {
        Constructor<? extends ZigBeeBaseChannelConverter> constructor;
        try {
            if (channelMap.get(channel.getChannelTypeUID()) == null) {
                logger.debug("{}: Channel converter for channel type {} is not implemented!", ieeeAddress,
                        channel.getUID().getId());
                return null;
            }
            constructor = channelMap.get(channel.getChannelTypeUID()).getConstructor();
            ZigBeeBaseChannelConverter instance = constructor.newInstance();

            instance.initialize(channel, coordinatorHandler, ieeeAddress, endpointId);
            return instance;
        } catch (Exception e) {
            logger.error("{}: Unable to create channel {}", ieeeAddress, channel.getUID(), e);
        }

        return null;
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void addZigBeeChannelConverterProvider(ZigBeeChannelConverterProvider zigBeeChannelConverterProvider) {
        channelMap.putAll(zigBeeChannelConverterProvider.getChannelConverters());
    }

    public void removeZigBeeChannelConverterProvider(ZigBeeChannelConverterProvider zigBeeChannelConverterProvider) {
        channelMap.keySet().removeAll(zigBeeChannelConverterProvider.getChannelConverters().keySet());
    }

    /**
     * Gets the cluster IDs that are supported by the converter
     *
     * @return Set of cluster IDs supported by the converter. The Set will be ordered by ascending ID
     */
    @Override
    public Set<Integer> getImplementedClientClusters() {
        Set<Integer> clusters = new TreeSet<>();
        try {
            for (Class<? extends ZigBeeBaseChannelConverter> converter : channelMap.values()) {
                Constructor<? extends ZigBeeBaseChannelConverter> constructor = converter.getConstructor();
                ZigBeeBaseChannelConverter instance = constructor.newInstance();

                clusters.addAll(instance.getImplementedClientClusters());
            }
        } catch (Exception e) {
            logger.error("Unable to consolidate client cluster IDs", e);
        }

        return clusters;
    }

    /**
     * Gets the cluster IDs that are supported by the converter
     *
     * @return Set of cluster IDs supported by the converter. The Set will be ordered by ascending ID
     */
    @Override
    public Set<Integer> getImplementedServerClusters() {
        Set<Integer> clusters = new TreeSet<>();
        try {
            for (Class<? extends ZigBeeBaseChannelConverter> converter : channelMap.values()) {
                Constructor<? extends ZigBeeBaseChannelConverter> constructor = converter.getConstructor();
                ZigBeeBaseChannelConverter instance = constructor.newInstance();

                clusters.addAll(instance.getImplementedServerClusters());
            }
        } catch (Exception e) {
            logger.error("Unable to consolidate server cluster IDs", e);
        }

        return clusters;
    }

}
