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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.State;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelWithOnOffCommand;
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
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        ZclAttribute onAttribute = new ZclAttribute(new ZclOnOffCluster(endpoint), 0, "OnOff", ZclDataType.BOOLEAN,
                false, false, false, false);
        ZclAttribute levelAttribute = new ZclAttribute(new ZclLevelControlCluster(endpoint), 0, "Level",
                ZclDataType.BOOLEAN, false, false, false, false);

        // The following sequence checks that the level is ignored if the OnOff state is OFF
        // Initial value of level is 100%
        onAttribute.updateValue(Boolean.TRUE);
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(PercentType.HUNDRED, stateCapture.getValue());

        // Set the level to ensure that level updates work before OnOff states are received
        levelAttribute.updateValue(Integer.valueOf(50));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new PercentType(20), stateCapture.getValue());

        // Turn off, and we should get OFF state
        onAttribute.updateValue(Boolean.FALSE);
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(OnOffType.OFF, stateCapture.getValue());

        // No update here, but we should remember the value for when it's turned on...
        levelAttribute.updateValue(Integer.valueOf(101));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Turn on, and use the last level received (20%)
        onAttribute.updateValue(Boolean.TRUE);
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(4)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new PercentType(40), stateCapture.getValue());

        // Set the level to 40% and make sure it's updated
        levelAttribute.updateValue(Integer.valueOf(50));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(5)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(new PercentType(20), stateCapture.getValue());
    }

    @Test
    public void testCommand() throws InterruptedException, ExecutionException {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZclOnOffCluster onoffCluster = Mockito.mock(ZclOnOffCluster.class);
        ZclLevelControlCluster levelControlCluster = Mockito.mock(ZclLevelControlCluster.class);
        Mockito.when(endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID)).thenReturn(onoffCluster);
        Mockito.when(endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID)).thenReturn(levelControlCluster);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        Future<Boolean> falseFuture = Mockito.mock(Future.class);
        Mockito.when(falseFuture.get()).thenReturn(Boolean.FALSE);
        Mockito.when(onoffCluster.discoverAttributes(false)).thenReturn(falseFuture);
        Mockito.when(levelControlCluster.discoverAttributes(false)).thenReturn(falseFuture);

        ZigBeeConverterSwitchLevel converter = new ZigBeeConverterSwitchLevel();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        ZclAttribute onAttribute = new ZclAttribute(new ZclOnOffCluster(endpoint), 0, "OnOff", ZclDataType.BOOLEAN,
                false, false, false, false);
        ZclAttribute levelAttribute = new ZclAttribute(new ZclLevelControlCluster(endpoint), 0, "Level",
                ZclDataType.BOOLEAN, false, false, false, false);

        onAttribute.updateValue(Boolean.FALSE);
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(OnOffType.OFF, stateCapture.getValue());

        levelAttribute.updateValue(Integer.valueOf(10));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(OnOffType.OFF, stateCapture.getAllValues().get(1));

        CommandResult commandResult = Mockito.mock(CommandResult.class);
        Mockito.when(commandResult.isError()).thenReturn(false);
        Mockito.when(commandResult.isTimeout()).thenReturn(false);
        Future<CommandResult> resultFuture = Mockito.mock(Future.class);
        Mockito.when(resultFuture.get()).thenReturn(commandResult);

        Mockito.when(levelControlCluster.sendCommand(ArgumentMatchers.any(MoveToLevelWithOnOffCommand.class)))
                .thenReturn(resultFuture);

        // We now have the state OFF and level 10
        // We want to send a command to set the level to 50% and make sure the state moves there cleanly
        converter.handleCommand(new PercentType(50));

        onAttribute.updateValue(Boolean.TRUE);
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        levelAttribute.updateValue(Integer.valueOf(150));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());

        Mockito.verify(thingHandler, Mockito.atLeastOnce()).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // We just make sure that the value doesn't drop down to ~4 from the attribute report of 10 above
        for (State value : stateCapture.getAllValues()) {
            System.out.println("out: " + value);
            if (value instanceof PercentType) {
                assertTrue(((PercentType) value).intValue() >= 50);
            }
        }
    }
}
