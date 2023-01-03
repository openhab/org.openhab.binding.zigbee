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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.command.TuyaButtonPressCommand;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.ThingUID;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeCommand;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeProfileType;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclMultistateInputBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.MoveHueCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Unit tests for the {@link ZigBeeConverterTuyaButtonTest}.
 *
 * @author Henning Sudbrock - initial contribution for {@link ZigBeeConverterGenericButtonTest}.
 * @author Daniel Schall - copied to {@link ZigBeeConverterTuyaButtonTest} and changed.
 */
public class ZigBeeConverterTuyaButtonTest {

    private ZigBeeConverterTuyaButton converter;

    private ZigBeeThingHandler thingHandler;
    private Channel channel;
    private ZigBeeCoordinatorHandler coordinatorHandler;
    private ZigBeeEndpoint endpoint;

    private Map<String, String> channelProperties;

    @BeforeEach
    public void setup() {
        IeeeAddress ieeeAddress = new IeeeAddress();
        int endpointId = 1;

        endpoint = mock(ZigBeeEndpoint.class);
        thingHandler = mock(ZigBeeThingHandler.class);

        channel = mock(Channel.class);
        channelProperties = new HashMap<>();
        when(channel.getProperties()).thenReturn(channelProperties);

        coordinatorHandler = mock(ZigBeeCoordinatorHandler.class);
        when(coordinatorHandler.getEndpoint(ieeeAddress, endpointId)).thenReturn(endpoint);
        when(coordinatorHandler.getLocalIeeeAddress()).thenReturn(ieeeAddress);
        when(coordinatorHandler.getLocalEndpointId(ArgumentMatchers.any(ZigBeeProfileType.class))).thenReturn(1);

        converter = new ZigBeeConverterTuyaButton();
        converter.initialize(channel, coordinatorHandler, ieeeAddress, endpointId);
    }

    @Test
    public void testBasicInitialization() {
        ZclCluster cluster = mockCluster(ZclOnOffCluster.CLUSTER_ID);
        boolean initResult = converter.initializeConverter(thingHandler);

        assertTrue(initResult);
        verify(cluster, times(1)).addCommandListener(converter);
        verify(cluster, times(1)).addAttributeListener(any(ZclAttributeListener.class));
    }

    @Test
    public void testDisposalRemvoesListeners() {
        ZclCluster cluster = mockCluster(ZclOnOffCluster.CLUSTER_ID);
        converter.initializeConverter(thingHandler);
        converter.disposeConverter();

        verify(cluster, times(1)).addCommandListener(converter);
        verify(cluster, times(1)).addAttributeListener(any(ZclAttributeListener.class));
        verify(cluster, times(1)).removeCommandListener(converter);
        verify(cluster, times(1)).removeAttributeListener(any(ZclAttributeListener.class));
    }

    @Test
    public void testCommandHandlerInstallation() {
        ZclCluster cluster = mockCluster(ZclOnOffCluster.CLUSTER_ID);
        converter.initializeConverter(thingHandler);

        verify(cluster, times(1)).addClientCommands(any(Map.class));
    }

    @Test
    public void testShortPressCommand() {
        mockCluster(ZclOnOffCluster.CLUSTER_ID);
        converter.initializeConverter(thingHandler);

        TuyaButtonPressCommand tuyaButtonPressCommand = 
            new TuyaButtonPressCommand(0);
        tuyaButtonPressCommand.setTransactionId(1);
        converter.commandReceived(tuyaButtonPressCommand);

        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
    }

    @Test
    public void testDoublePressCommand() {
        mockCluster(ZclOnOffCluster.CLUSTER_ID);
        converter.initializeConverter(thingHandler);

        TuyaButtonPressCommand tuyaButtonPressCommand = 
            new TuyaButtonPressCommand(1);
        tuyaButtonPressCommand.setTransactionId(1);
        converter.commandReceived(tuyaButtonPressCommand);

        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
    }

    @Test
    public void testLongPressCommand() {
        mockCluster(ZclOnOffCluster.CLUSTER_ID);
        converter.initializeConverter(thingHandler);

        TuyaButtonPressCommand tuyaButtonPressCommand = 
            new TuyaButtonPressCommand(2);
        tuyaButtonPressCommand.setTransactionId(1);
        converter.commandReceived(tuyaButtonPressCommand);

        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
    }

    @Test
    public void testTwoCommands() {
        mockCluster(ZclOnOffCluster.CLUSTER_ID);
        converter.initializeConverter(thingHandler);

        TuyaButtonPressCommand tuyaButtonPressCommand = 
            new TuyaButtonPressCommand(0);
        tuyaButtonPressCommand.setTransactionId(1);
        converter.commandReceived(tuyaButtonPressCommand);
        tuyaButtonPressCommand.setTransactionId(2);
        converter.commandReceived(tuyaButtonPressCommand);

        verify(thingHandler, times(2)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
    }

    @Test
    public void testDuplicateTransactionCommand() {
        mockCluster(ZclOnOffCluster.CLUSTER_ID);
        converter.initializeConverter(thingHandler);

        TuyaButtonPressCommand tuyaButtonPressCommand = 
            new TuyaButtonPressCommand(0);
        tuyaButtonPressCommand.setTransactionId(1);
        converter.commandReceived(tuyaButtonPressCommand);
        converter.commandReceived(tuyaButtonPressCommand);

        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
    }

    private ZclCluster mockCluster(int clusterId) {
        ZclCluster cluster = mock(ZclCluster.class);
        when(cluster.getClusterId()).thenReturn(clusterId);
        when(cluster.bind(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt())).thenReturn(
            CompletableFuture.completedFuture(new CommandResult(ZigBeeStatus.SUCCESS, new ZigBeeCommand())));
        when(endpoint.getOutputCluster(clusterId)).thenReturn(cluster);
        when(endpoint.getInputCluster(clusterId)).thenReturn(cluster);
        return cluster;
    }
}
