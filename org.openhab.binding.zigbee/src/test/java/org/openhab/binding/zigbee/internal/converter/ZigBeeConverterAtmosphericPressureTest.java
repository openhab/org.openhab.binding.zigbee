/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

        ZclAttribute attributeMeasuredVal = Mockito.mock(ZclAttribute.class);
        Mockito.when(attributeMeasuredVal.getCluster()).thenReturn(ZclClusterType.PRESSURE_MEASUREMENT);
        Mockito.when(attributeMeasuredVal.getId()).thenReturn(ZclPressureMeasurementCluster.ATTR_MEASUREDVALUE);
        Mockito.when(attributeMeasuredVal.getLastValue()).thenReturn(Integer.valueOf(1234));

        ZclAttribute attributeScaledVal = Mockito.mock(ZclAttribute.class);
        Mockito.when(attributeScaledVal.getCluster()).thenReturn(ZclClusterType.PRESSURE_MEASUREMENT);
        Mockito.when(attributeScaledVal.getId()).thenReturn(ZclPressureMeasurementCluster.ATTR_SCALEDVALUE);
        Mockito.when(attributeScaledVal.getLastValue()).thenReturn(Integer.valueOf(12456));

        ZclAttribute attributeScaledVal2 = Mockito.mock(ZclAttribute.class);
        Mockito.when(attributeScaledVal2.getCluster()).thenReturn(ZclClusterType.PRESSURE_MEASUREMENT);
        Mockito.when(attributeScaledVal2.getId()).thenReturn(ZclPressureMeasurementCluster.ATTR_SCALEDVALUE);
        Mockito.when(attributeScaledVal2.getLastValue()).thenReturn(Integer.valueOf(12556));

        ZclAttribute attributeScale = Mockito.mock(ZclAttribute.class);
        Mockito.when(attributeScale.getCluster()).thenReturn(ZclClusterType.PRESSURE_MEASUREMENT);
        Mockito.when(attributeScale.getId()).thenReturn(ZclPressureMeasurementCluster.ATTR_SCALE);
        Mockito.when(attributeScale.getLastValue()).thenReturn(Integer.valueOf(-1));

        // Unscaled value, so it's updated
        converter.attributeUpdated(attributeMeasuredVal);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertTrue(stateCapture.getValue() instanceof QuantityType);
        QuantityType<Pressure> value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(HECTO(SIUnits.PASCAL), value.getUnit());
        assertEquals(1234, value.doubleValue(), 0.01);

        // Scaled value, but no scale yet, so no update
        converter.attributeUpdated(attributeScaledVal);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Scaled value, with scale, so updated
        converter.attributeUpdated(attributeScale);
        converter.attributeUpdated(attributeScaledVal);
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertTrue(stateCapture.getValue() instanceof QuantityType);
        value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(HECTO(SIUnits.PASCAL), value.getUnit());
        assertEquals(1245.6, value.doubleValue(), 0.01);

        // Measured value, but after we received the scale, so no update
        converter.attributeUpdated(attributeMeasuredVal);
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Scaled value, with scale, so updated
        converter.attributeUpdated(attributeScale);
        converter.attributeUpdated(attributeScaledVal);
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertTrue(stateCapture.getValue() instanceof QuantityType);
        value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(HECTO(SIUnits.PASCAL), value.getUnit());
        assertEquals(1245.6, value.doubleValue(), 0.01);

        //
        converter.attributeUpdated(attributeScale);
        converter.attributeUpdated(attributeMeasuredVal);
        converter.attributeUpdated(attributeScaledVal2);
        Mockito.verify(thingHandler, Mockito.times(4)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(1255.6, value.doubleValue(), 0.01);

        //
        converter.attributeUpdated(attributeMeasuredVal);
        converter.attributeUpdated(attributeScaledVal);
        converter.attributeUpdated(attributeScale);
        Mockito.verify(thingHandler, Mockito.times(5)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(1245.6, value.doubleValue(), 0.01);

    }
}
