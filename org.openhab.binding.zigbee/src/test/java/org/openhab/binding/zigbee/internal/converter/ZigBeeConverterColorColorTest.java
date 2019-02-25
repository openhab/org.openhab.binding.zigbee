/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import static org.junit.Assert.assertEquals;

import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;
import com.zsmartsystems.zigbee.zcl.protocol.ZclStandardClusterType;

/**
 * Tests for color converter.
 * Note that the converter uses a timer to ensure consistency of color updates, so tests of color updates will need to
 * account for this!
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeConverterColorColorTest {

    @Test
    public void testAttributeUpdated() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterColorColor converter = new ZigBeeConverterColorColor();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(thingHandler, channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);

        ZclAttribute onAttribute = new ZclAttribute(ZclStandardClusterType.ON_OFF, 0, "OnOff", ZclDataType.BOOLEAN,
                false, false, false, false);
        ZclAttribute levelAttribute = new ZclAttribute(ZclStandardClusterType.LEVEL_CONTROL, 0, "Level",
                ZclDataType.BOOLEAN, false, false, false, false);

        // The following sequence checks that the level is ignored if the OnOff state is OFF
        // Note that the converter assumes the default HSB is 0,0,100, so this is returned first.
        onAttribute.updateValue(new Boolean(true));
        converter.attributeUpdated(onAttribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(new HSBType("0,0,100"), stateCapture.getValue());

        // Set the level to ensure that when no OnOff has been received, state updates are made
        levelAttribute.updateValue(new Integer(50));
        converter.attributeUpdated(levelAttribute);
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new HSBType("0,0,20"), stateCapture.getValue());

        // Turn OFF, and state should be OFF (indeed 0,0,0)
        onAttribute.updateValue(new Boolean(false));
        converter.attributeUpdated(onAttribute);
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(new HSBType("0,0,0"), stateCapture.getValue());

        // No update here since state is OFF, but we remember the level (20%)...
        levelAttribute.updateValue(new Integer(101));
        converter.attributeUpdated(levelAttribute);
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // When turned ON, we are set to the last level
        onAttribute.updateValue(new Boolean(true));
        converter.attributeUpdated(onAttribute);
        Mockito.verify(thingHandler, Mockito.times(4)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new HSBType("0,0,40"), stateCapture.getValue());

        // Set the level and ensure it updates
        levelAttribute.updateValue(new Integer(50));
        converter.attributeUpdated(levelAttribute);
        Mockito.verify(thingHandler, Mockito.times(5)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new HSBType("0,0,20"), stateCapture.getValue());
    }

    @Test
    public void testAttributeNotUpdatedWhenColorModeIsColorTemperature() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterColorColor converter = new ZigBeeConverterColorColor();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(thingHandler, channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);

        ZclAttribute colorModeAttribute = new ZclAttribute(ZclClusterType.COLOR_CONTROL, 8, "ColorMode",
                ZclDataType.ENUMERATION_8_BIT, false, false, false, false);
        ZclAttribute onAttribute = new ZclAttribute(ZclClusterType.ON_OFF, 0, "OnOff", ZclDataType.BOOLEAN, false,
                false, false, false);
        ZclAttribute levelAttribute = new ZclAttribute(ZclClusterType.LEVEL_CONTROL, 0, "Level", ZclDataType.BOOLEAN,
                false, false, false, false);
        ZclAttribute currentHueAttribute = new ZclAttribute(ZclClusterType.COLOR_CONTROL, 0, "CurrentHue",
                ZclDataType.UNSIGNED_8_BIT_INTEGER, false, false, false, false);
        ZclAttribute currentSaturationAttribute = new ZclAttribute(ZclClusterType.COLOR_CONTROL, 1, "CurrentSaturation",
                ZclDataType.UNSIGNED_8_BIT_INTEGER, false, false, false, false);
        ZclAttribute currentXAttribute = new ZclAttribute(ZclClusterType.COLOR_CONTROL, 3, "CurrentX",
                ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, false, false);
        ZclAttribute currentYAttribute = new ZclAttribute(ZclClusterType.COLOR_CONTROL, 4, "CurrentY",
                ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, false, false);

        // Update the color mode to COLOR_TEMPERATURE and ensure that the state is set to UNDEF
        colorModeAttribute.updateValue(ColorModeEnum.COLORTEMPERATURE.getKey());
        converter.attributeUpdated(colorModeAttribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(UnDefType.UNDEF, stateCapture.getValue());

        // Turn ON and ensure that the channel is not set
        onAttribute.updateValue(new Boolean(true));
        converter.attributeUpdated(onAttribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the level and ensure that the channel is not set
        levelAttribute.updateValue(new Integer(50));
        converter.attributeUpdated(levelAttribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the hue and ensure that the channel is not set
        currentHueAttribute.updateValue(new Integer(10));
        converter.attributeUpdated(currentHueAttribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the saturation and ensure that the channel is not set
        currentSaturationAttribute.updateValue(new Integer(10));
        converter.attributeUpdated(currentSaturationAttribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the currentX and ensure that the channel is not set
        currentXAttribute.updateValue(new Integer(10));
        converter.attributeUpdated(currentXAttribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the level and ensure that the channel is not set
        currentYAttribute.updateValue(new Integer(10));
        converter.attributeUpdated(currentYAttribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

    }

}
