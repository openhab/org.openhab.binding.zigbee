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

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
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
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Tests for Level converter
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeConverterSwitchLevelTest {

    @Test
    public void testAttributeUpdated() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterSwitchLevel converter = new ZigBeeConverterSwitchLevel();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(thingHandler, channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);

        ZclAttribute onAttribute = new ZclAttribute(ZclClusterType.ON_OFF, 0, "OnOff", ZclDataType.BOOLEAN, false,
                false, false, false);
        ZclAttribute levelAttribute = new ZclAttribute(ZclClusterType.LEVEL_CONTROL, 0, "Level", ZclDataType.BOOLEAN,
                false, false, false, false);

        // The following sequence checks that the level is ignored if the OnOff state is OFF
        onAttribute.updateValue(new Boolean(true));
        converter.attributeUpdated(onAttribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(OnOffType.ON, stateCapture.getValue());

        onAttribute.updateValue(new Boolean(false));
        converter.attributeUpdated(onAttribute);
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(OnOffType.OFF, stateCapture.getValue());

        // No update here...
        levelAttribute.updateValue(new Integer(50));
        converter.attributeUpdated(levelAttribute);
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        onAttribute.updateValue(new Boolean(true));
        converter.attributeUpdated(onAttribute);
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(OnOffType.ON, stateCapture.getValue());

        converter.attributeUpdated(levelAttribute);
        Mockito.verify(thingHandler, Mockito.times(4)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(new PercentType(20), stateCapture.getValue());
    }
}
