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

import static org.junit.jupiter.api.Assertions.*;

import javax.measure.quantity.Temperature;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.State;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclTemperatureMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Tests for Temperature converter
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeConverterTemperatureTest {

    @Test
    public void testAttributeUpdated() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZclTemperatureMeasurementCluster cluster = Mockito.mock(ZclTemperatureMeasurementCluster.class);
        Mockito.when(endpoint.getInputCluster(ZclClusterType.TEMPERATURE_MEASUREMENT.getId())).thenReturn(cluster);

        ZigBeeConverterTemperature converter = new ZigBeeConverterTemperature();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        ZclAttribute attribute = Mockito.mock(ZclAttribute.class);
        Mockito.when(attribute.getClusterType()).thenReturn(ZclClusterType.TEMPERATURE_MEASUREMENT);
        Mockito.when(attribute.getId()).thenReturn(ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE);

        converter.attributeUpdated(attribute, Integer.valueOf(2345));
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertTrue(stateCapture.getValue() instanceof QuantityType);
        QuantityType<Temperature> value = (QuantityType<Temperature>) stateCapture.getValue();
        assertEquals(SIUnits.CELSIUS, value.getUnit());
        assertEquals(23.45, value.doubleValue(), 0.01);
    }
}
