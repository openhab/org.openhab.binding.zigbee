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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.State;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclMeteringCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Tests for metering converter
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeConverterMeteringInstantaneousDemandTest {

    @Test
    public void testAttributeUpdated() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        ZclMeteringCluster cluster = Mockito.mock(ZclMeteringCluster.class);
        ZclAttribute divAttribute = Mockito.mock(ZclAttribute.class);
        ZclAttribute multAttribute = Mockito.mock(ZclAttribute.class);
        ZclAttribute valueAttribute = Mockito.mock(ZclAttribute.class);
        Mockito.when(cluster.getAttribute(ZclMeteringCluster.ATTR_INSTANTANEOUSDEMAND)).thenReturn(valueAttribute);
        Mockito.when(cluster.getAttribute(ZclMeteringCluster.ATTR_DIVISOR)).thenReturn(divAttribute);
        Mockito.when(cluster.getAttribute(ZclMeteringCluster.ATTR_MULTIPLIER)).thenReturn(multAttribute);
        Mockito.when(divAttribute.readValue(Long.MAX_VALUE)).thenReturn(10000);
        Mockito.when(multAttribute.readValue(Long.MAX_VALUE)).thenReturn(1);
        Mockito.when(valueAttribute.readValue(Long.MAX_VALUE)).thenReturn(65);

        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);
        Mockito.when(endpoint.getInputCluster(ZclMeteringCluster.CLUSTER_ID)).thenReturn(cluster);

        ZigBeeConverterMeteringInstantaneousDemand converter = new ZigBeeConverterMeteringInstantaneousDemand();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        ZclAttribute attribute = new ZclAttribute(new ZclMeteringCluster(endpoint),
                ZclMeteringCluster.ATTR_INSTANTANEOUSDEMAND, "Demand", ZclDataType.SIGNED_24_BIT_INTEGER, false, false,
                false, false);

        attribute.updateValue(65);
        converter.attributeUpdated(attribute, attribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(DecimalType.valueOf("0.0065"), stateCapture.getValue());
    }
}
