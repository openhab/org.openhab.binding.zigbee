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
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.MoveHueCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Unit tests for the {@link ZigBeeConverterGenericButton}.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class ZigBeeConverterGenericButtonTest {

    private ZigBeeConverterGenericButton converter;

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

        converter = new ZigBeeConverterGenericButton();
        converter.initialize(channel, coordinatorHandler, ieeeAddress, endpointId);
    }

    @Test
    public void converterInitializationForCommandBindsToCorrectCluster() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_command_id", "0x0017");

        ZclCluster cluster = mockCluster(8);

        boolean initResult = converter.initializeConverter(thingHandler);

        assertTrue(initResult);
        verify(cluster, times(1)).addCommandListener(converter);
        verify(cluster, times(0)).addAttributeListener(any(ZclAttributeListener.class));
    }

    @Test
    public void converterInitializationForAttributeBindsToCorrectCluster() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_attribute_id", "0x0017");
        channelProperties.put("zigbee_shortpress_attribute_value", "2");

        ZclCluster cluster = mockCluster(8);

        boolean initResult = converter.initializeConverter(thingHandler);

        assertTrue(initResult);
        verify(cluster, times(1)).addAttributeListener(converter);
        verify(cluster, times(0)).addCommandListener(any(ZclCommandListener.class));
    }

    @Test
    public void converterInitializationBindsToClusterOnlyOnce() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_command_id", "0x0017");

        channelProperties.put("zigbee_longpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_longpress_command_id", "0x0018");

        channelProperties.put("zigbee_doublepress_cluster_id", "0x0009");
        channelProperties.put("zigbee_doublepress_command_id", "0x0017");

        ZclCluster cluster8 = mockCluster(8);
        ZclCluster cluster9 = mockCluster(9);

        boolean initResult = converter.initializeConverter(thingHandler);

        assertTrue(initResult);
        verify(cluster8, times(1)).addCommandListener(converter);
        verify(cluster9, times(1)).addCommandListener(converter);
    }

    @Test
    public void converterInitializationClusterIdIsMandatory() {
        channelProperties.put("zigbee_shortpress_command_id", "0x0017");
        boolean initResult = converter.initializeConverter(thingHandler);
        assertFalse(initResult);
    }

    @Test
    public void converterInitializationCommandIdOrAttributeIsMandatory() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x008");
        mockCluster(8);
        boolean initResult = converter.initializeConverter(thingHandler);
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithUnparseableClusterId() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0xEFGH");
        channelProperties.put("zigbee_shortpress_command_id", "123");
        boolean initResult = converter.initializeConverter(thingHandler);
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithUnparseableCommandId() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_command_id", "abc");
        mockCluster(8);
        boolean initResult = converter.initializeConverter(thingHandler);
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithUnparseableAttributeId() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_attribute_id", "abc");
        channelProperties.put("zigbee_shortpress_attribute_value", "abc");
        mockCluster(8);
        boolean initResult = converter.initializeConverter(thingHandler);
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithIncompleteParamSpecWithoutValue() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_command_id", "0xabc");
        channelProperties.put("zigbee_shortpress_parameter_name", "mode");
        mockCluster(8);
        boolean initResult = converter.initializeConverter(thingHandler);
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithIncompleteParamSpecWithoutName() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_command_id", "0xabc");
        channelProperties.put("zigbee_shortpress_parameter_value", "1");
        mockCluster(8);
        boolean initResult = converter.initializeConverter(thingHandler);
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithIncompleteAttributeSpec() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_attribute_id", "1");
        mockCluster(8);
        boolean initResult = converter.initializeConverter(thingHandler);
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithCommandAndAttribute() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_command_id", "0xabc");
        channelProperties.put("zigbee_shortpress_attribute_id", "1");
        channelProperties.put("zigbee_shortpress_attribute_value", "2");
        mockCluster(8);
        boolean initResult = converter.initializeConverter(thingHandler);
        assertFalse(initResult);
    }

    @Test
    public void cannotInitializeConverterWithoutChannel() {
        assertNull(converter.getChannel(mock(ThingUID.class), endpoint));
    }

    @Test
    public void commandListenersAreRemovedOnDispose() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_command_id", "0x0017");
        ZclCluster cluster = mockCluster(8);

        converter.initializeConverter(thingHandler);
        converter.disposeConverter();

        verify(cluster, times(1)).removeCommandListener(converter);
        verify(cluster, times(0)).removeAttributeListener(any(ZclAttributeListener.class));
    }

    @Test
    public void attributeListenersAreRemovedOnDispose() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_attribute_id", "0x0017");
        channelProperties.put("zigbee_shortpress_attribute_value", "2");
        ZclCluster cluster = mockCluster(8);

        converter.initializeConverter(thingHandler);
        converter.disposeConverter();

        verify(cluster, times(1)).removeAttributeListener(converter);
        verify(cluster, times(0)).removeCommandListener(any(ZclCommandListener.class));
    }

    @Test
    public void commandWithoutSpecifiedParamIsHandled() {
        channelProperties.put("zigbee_shortpress_cluster_id", "768");
        channelProperties.put("zigbee_shortpress_command_id", "0x01");
        mockCluster(768);
        converter.initializeConverter(thingHandler);

        converter.commandReceived(new MoveHueCommand());

        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
    }

    @Test
    public void commandWithMatchingSpecifiedParamIsHandled() {
        channelProperties.put("zigbee_shortpress_cluster_id", "768");
        channelProperties.put("zigbee_shortpress_command_id", "0x01");
        channelProperties.put("zigbee_shortpress_parameter_name", "moveMode");
        channelProperties.put("zigbee_shortpress_parameter_value", "1");
        mockCluster(768);
        converter.initializeConverter(thingHandler);

        MoveHueCommand moveHueCommand = new MoveHueCommand();
        moveHueCommand.setMoveMode(1);
        converter.commandReceived(moveHueCommand);

        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
    }

    @Test
    public void attributeWithMatchingValueIsHandled() {
        channelProperties.put("zigbee_shortpress_cluster_id", "768");
        channelProperties.put("zigbee_shortpress_attribute_id", "85");
        channelProperties.put("zigbee_shortpress_attribute_value", "1");
        mockCluster(768);
        converter.initializeConverter(thingHandler);

        ZclAttribute attribute = new ZclAttribute(new ZclMultistateInputBasicCluster(endpoint), 85, "foo",
                ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, true, true);
        attribute.updateValue(1);
        converter.attributeUpdated(attribute, attribute.getLastValue());

        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
    }

    @Disabled
    @Test
    public void commandWithNonMatchingSpecifiedParamNameIsNotHandled() {
        channelProperties.put("zigbee_shortpress_cluster_id", "768");
        channelProperties.put("zigbee_shortpress_command_id", "0x01");
        channelProperties.put("zigbee_shortpress_parameter_name", "blueMode");
        channelProperties.put("zigbee_shortpress_parameter_value", "1");
        mockCluster(768);
        converter.initializeConverter(thingHandler);

        MoveHueCommand moveHueCommand = new MoveHueCommand();
        moveHueCommand.setMoveMode(1);
        converter.commandReceived(moveHueCommand);

        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
    }

    @Test
    public void commandWithNonMatchingSpecifiedParamValueIsNotHandled() {
        channelProperties.put("zigbee_shortpress_cluster_id", "768");
        channelProperties.put("zigbee_shortpress_command_id", "0x01");
        channelProperties.put("zigbee_shortpress_parameter_name", "moveMode");
        channelProperties.put("zigbee_shortpress_parameter_value", "1");
        mockCluster(768);
        converter.initializeConverter(thingHandler);

        MoveHueCommand moveHueCommand = new MoveHueCommand();
        moveHueCommand.setMoveMode(0);
        converter.commandReceived(moveHueCommand);

        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
    }

    @Test
    public void attributeWithNonMatchingValueIsNotHandled() {
        channelProperties.put("zigbee_shortpress_cluster_id", "768");
        channelProperties.put("zigbee_shortpress_attribute_id", "85");
        channelProperties.put("zigbee_shortpress_attribute_value", "1");
        mockCluster(768);
        converter.initializeConverter(thingHandler);

        ZclAttribute attribute = new ZclAttribute(new ZclMultistateInputBasicCluster(endpoint), 85, "foo",
                ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, true, true);
        attribute.updateValue(2);
        converter.attributeUpdated(attribute, attribute.getLastValue());

        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
    }

    @Test
    public void commandTypeIsCorrectlyDetected() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_command_id", "0x0017");

        channelProperties.put("zigbee_longpress_cluster_id", "768");
        channelProperties.put("zigbee_longpress_command_id", "0x01");

        channelProperties.put("zigbee_doublepress_cluster_id", "0x0009");
        channelProperties.put("zigbee_doublepress_command_id", "0x0017");

        mockCluster(768);
        converter.initializeConverter(thingHandler);

        converter.commandReceived(new MoveHueCommand());

        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
    }

    @Test
    public void attributeTypeIsCorrectlyDetected() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_attribute_id", "0x0017");
        channelProperties.put("zigbee_shortpress_attribute_value", "0x01");

        channelProperties.put("zigbee_longpress_cluster_id", "768");
        channelProperties.put("zigbee_longpress_attribute_id", "0x0017");
        channelProperties.put("zigbee_longpress_attribute_value", "2");

        channelProperties.put("zigbee_doublepress_cluster_id", "0x0009");
        channelProperties.put("zigbee_doublepress_attribute_id", "0x0017");
        channelProperties.put("zigbee_doublepress_attribute_value", "0x03");

        mockCluster(768);
        converter.initializeConverter(thingHandler);

        ZclAttribute attribute = new ZclAttribute(new ZclMultistateInputBasicCluster(endpoint), 0x17, "foo",
                ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, true, true);
        attribute.updateValue(2);
        converter.attributeUpdated(attribute, attribute.getLastValue());

        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
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
