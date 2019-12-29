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

import static org.junit.Assert.assertEquals;

import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.State;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
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

        ZclAttribute onAttribute = new ZclAttribute(new ZclOnOffCluster(endpoint), 0, "OnOff", ZclDataType.BOOLEAN,
                false, false, false, false);
        ZclAttribute levelAttribute = new ZclAttribute(new ZclLevelControlCluster(endpoint), 0, "Level",
                ZclDataType.BOOLEAN, false, false, false, false);

        // The following sequence checks that the level is ignored if the OnOff state is OFF
        // Initial value of level is 100%
        onAttribute.updateValue(new Boolean(true));
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(PercentType.HUNDRED, stateCapture.getValue());

        // Set the level to ensure that level updates work before OnOff states are received
        levelAttribute.updateValue(new Integer(50));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new PercentType(20), stateCapture.getValue());

        // Turn off, and we should get OFF state
        onAttribute.updateValue(new Boolean(false));
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(OnOffType.OFF, stateCapture.getValue());

        // No update here, but we should remember the value for when it's turned on...
        levelAttribute.updateValue(new Integer(101));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Turn on, and use the last level received (20%)
        onAttribute.updateValue(new Boolean(true));
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(4)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new PercentType(40), stateCapture.getValue());

        // Set the level to 40% and make sure it's updated
        levelAttribute.updateValue(new Integer(50));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(5)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(new PercentType(20), stateCapture.getValue());
    }
}
