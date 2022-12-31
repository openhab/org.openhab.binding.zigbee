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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterProvider;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.type.ChannelTypeUID;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;

/**
 * Testing the {@link ZigBeeChannelConverterFactoryImpl}.
 *
 * @author Thomas Höfer - Initial contribution
 */
public final class ZigBeeChannelConverterFactoryImplTest {

    private final ThingUID thingUID = new ThingUID("zigbee:generic:thing");

    private final Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> converters = new HashMap<>();

    private final ZigBeeEndpoint endpoint = createEndpoint();

    private ZigBeeChannelConverterFactoryImpl factory;
    private ZigBeeChannelConverterProvider provider;

    @BeforeEach
    public void setup() {
        factory = new ZigBeeChannelConverterFactoryImpl();

        converters.put(ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF, ZigBeeConverterSwitchOnoff.class);
        converters.put(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL, ZigBeeConverterSwitchLevel.class);

        provider = new TestZigBeeChannelConverterProvider(converters);
    }

    @Test
    public void testNoProvider() {
        Collection<Channel> channels = factory.getChannels(thingUID, endpoint);
        assertEquals(0, channels.size());
    }

    @Test
    public void testAddProvider() {
        final int expectedConvertersConfiguredForMockedEndpoint = converters.size();
        factory.addZigBeeChannelConverterProvider(provider);
        Collection<Channel> channels = factory.getChannels(thingUID, endpoint);
        // -1 because of consolidation
        assertEquals(expectedConvertersConfiguredForMockedEndpoint - 1, channels.size());
    }

    @Test
    public void testRemoveProvider() {
        factory.addZigBeeChannelConverterProvider(provider);
        factory.removeZigBeeChannelConverterProvider(provider);
        Collection<Channel> channels = factory.getChannels(thingUID, endpoint);
        assertEquals(0, channels.size());
    }

    private ZigBeeEndpoint createEndpoint() {
        ZigBeeEndpoint endpoint = mock(ZigBeeEndpoint.class);
        ZclCluster cluster = mock(ZclCluster.class);
        when(endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID)).thenReturn(cluster);
        when(endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID)).thenReturn(cluster);
        when(endpoint.getEndpointId()).thenReturn(1);
        return endpoint;
    }

    private final class TestZigBeeChannelConverterProvider implements ZigBeeChannelConverterProvider {

        private final Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> channelConverters;

        private TestZigBeeChannelConverterProvider(
                Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> channelConverters) {
            this.channelConverters = channelConverters;
        }

        @Override
        public Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> getChannelConverters() {
            return this.channelConverters;
        }
    }
}
