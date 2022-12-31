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
import static org.openhab.core.library.unit.MetricPrefix.HECTO;

import javax.measure.quantity.Pressure;

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
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        ZclAttribute attributeMeasuredVal = Mockito.mock(ZclAttribute.class);
        Mockito.when(attributeMeasuredVal.getClusterType()).thenReturn(ZclClusterType.PRESSURE_MEASUREMENT);
        Mockito.when(attributeMeasuredVal.getId()).thenReturn(ZclPressureMeasurementCluster.ATTR_MEASUREDVALUE);

        ZclAttribute attributeScaledVal = Mockito.mock(ZclAttribute.class);
        Mockito.when(attributeScaledVal.getClusterType()).thenReturn(ZclClusterType.PRESSURE_MEASUREMENT);
        Mockito.when(attributeScaledVal.getId()).thenReturn(ZclPressureMeasurementCluster.ATTR_SCALEDVALUE);

        ZclAttribute attributeScaledVal2 = Mockito.mock(ZclAttribute.class);
        Mockito.when(attributeScaledVal2.getClusterType()).thenReturn(ZclClusterType.PRESSURE_MEASUREMENT);
        Mockito.when(attributeScaledVal2.getId()).thenReturn(ZclPressureMeasurementCluster.ATTR_SCALEDVALUE);

        ZclAttribute attributeScale = Mockito.mock(ZclAttribute.class);
        Mockito.when(attributeScale.getClusterType()).thenReturn(ZclClusterType.PRESSURE_MEASUREMENT);
        Mockito.when(attributeScale.getId()).thenReturn(ZclPressureMeasurementCluster.ATTR_SCALE);
        Mockito.when(attributeScale.getLastValue()).thenReturn(Integer.valueOf(-1));

        // Unscaled value, so it's updated
        converter.attributeUpdated(attributeMeasuredVal, Integer.valueOf(1234));
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertTrue(stateCapture.getValue() instanceof QuantityType);
        QuantityType<Pressure> value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(HECTO(SIUnits.PASCAL), value.getUnit());
        assertEquals(1234, value.doubleValue(), 0.01);

        // Scaled value, but no scale yet, so no update
        converter.attributeUpdated(attributeScaledVal, Integer.valueOf(12456));
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Scaled value, with scale, so updated
        converter.attributeUpdated(attributeScale, Integer.valueOf(-1));
        converter.attributeUpdated(attributeScaledVal, Integer.valueOf(12456));
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertTrue(stateCapture.getValue() instanceof QuantityType);
        value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(HECTO(SIUnits.PASCAL), value.getUnit());
        assertEquals(1245.6, value.doubleValue(), 0.01);

        // Measured value, but after we received the scale, so no update
        converter.attributeUpdated(attributeMeasuredVal, Integer.valueOf(1234));
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Scaled value, with scale, so updated
        converter.attributeUpdated(attributeScale, Integer.valueOf(-1));
        converter.attributeUpdated(attributeScaledVal, Integer.valueOf(12456));
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertTrue(stateCapture.getValue() instanceof QuantityType);
        value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(HECTO(SIUnits.PASCAL), value.getUnit());
        assertEquals(1245.6, value.doubleValue(), 0.01);

        //
        converter.attributeUpdated(attributeScale, Integer.valueOf(-1));
        converter.attributeUpdated(attributeMeasuredVal, Integer.valueOf(1234));
        converter.attributeUpdated(attributeScaledVal2, Integer.valueOf(12556));
        Mockito.verify(thingHandler, Mockito.times(4)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(1255.6, value.doubleValue(), 0.01);

        //
        converter.attributeUpdated(attributeMeasuredVal, Integer.valueOf(1234));
        converter.attributeUpdated(attributeScaledVal, Integer.valueOf(12456));
        converter.attributeUpdated(attributeScale, Integer.valueOf(-1));
        Mockito.verify(thingHandler, Mockito.times(5)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        value = (QuantityType<Pressure>) stateCapture.getValue();
        assertEquals(1245.6, value.doubleValue(), 0.01);

    }
}
