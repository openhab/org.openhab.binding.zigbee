/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import static org.eclipse.smarthome.core.library.unit.MetricPrefix.HECTO;
import static org.junit.Assert.*;

import javax.measure.quantity.Pressure;

import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.State;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPressureMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Tests for Pressure converter
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeConverterAtmosphericPressureTest {

    @Test
    public void testAttributeUpdated() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterAtmosphericPressure converter = new ZigBeeConverterAtmosphericPressure();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(thingHandler, channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);

        ZclAttribute attribute = Mockito.mock(ZclAttribute.class);
        Mockito.when(attribute.getCluster()).thenReturn(ZclClusterType.PRESSURE_MEASUREMENT);
        Mockito.when(attribute.getId()).thenReturn(ZclPressureMeasurementCluster.ATTR_MEASUREDVALUE);
        Mockito.when(attribute.getLastValue()).thenReturn(Integer.valueOf(1234));

        converter.attributeUpdated(attribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertTrue(stateCapture.getValue() instanceof QuantityType);
        QuantityType<Pressure> value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(HECTO(SIUnits.PASCAL), value.getUnit());
        assertEquals(1234, value.doubleValue(), 0.01);
    }
}
