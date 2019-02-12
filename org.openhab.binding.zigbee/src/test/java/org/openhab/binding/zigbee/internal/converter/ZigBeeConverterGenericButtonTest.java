package org.openhab.binding.zigbee.internal.converter;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.CommonTriggerEvents;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeCommand;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeProfileType;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.MoveHueCommand;

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

    @Before
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
        converter.initialize(thingHandler, channel, coordinatorHandler, ieeeAddress, endpointId);
    }

    @Test
    public void converterInitializationForCommandBindsToCorrectCluster() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_command_id", "0x0017");

        ZclCluster cluster = mockCluster(8);

        boolean initResult = converter.initializeConverter();

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

        boolean initResult = converter.initializeConverter();

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

        boolean initResult = converter.initializeConverter();

        assertTrue(initResult);
        verify(cluster8, times(1)).addCommandListener(converter);
        verify(cluster9, times(1)).addCommandListener(converter);
    }

    @Test
    public void converterInitializationClusterIdIsMandatory() {
        channelProperties.put("zigbee_shortpress_command_id", "0x0017");
        boolean initResult = converter.initializeConverter();
        assertFalse(initResult);
    }

    @Test
    public void converterInitializationCommandIdOrAttributeIsMandatory() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x008");
        mockCluster(8);
        boolean initResult = converter.initializeConverter();
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithUnparseableClusterId() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0xEFGH");
        channelProperties.put("zigbee_shortpress_command_id", "123");
        boolean initResult = converter.initializeConverter();
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithUnparseableCommandId() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_command_id", "abc");
        mockCluster(8);
        boolean initResult = converter.initializeConverter();
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithUnparseableAttributeId() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_attribute_id", "abc");
        channelProperties.put("zigbee_shortpress_attribute_value", "abc");
        mockCluster(8);
        boolean initResult = converter.initializeConverter();
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithIncompleteParamSpecWithoutValue() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_command_id", "0xabc");
        channelProperties.put("zigbee_shortpress_parameter_name", "mode");
        mockCluster(8);
        boolean initResult = converter.initializeConverter();
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithIncompleteParamSpecWithoutName() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_command_id", "0xabc");
        channelProperties.put("zigbee_shortpress_parameter_value", "1");
        mockCluster(8);
        boolean initResult = converter.initializeConverter();
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithIncompleteAttributeSpec() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x8");
        channelProperties.put("zigbee_shortpress_attribute_id", "1");
        mockCluster(8);
        boolean initResult = converter.initializeConverter();
        assertFalse(initResult);
    }

    @Test
    public void converterCannotInitializeWithCommandAndAttribute() {
        channelProperties.put("zigbee_shortpress_cluster_id", "0x0008");
        channelProperties.put("zigbee_shortpress_command_id", "0xabc");
        channelProperties.put("zigbee_shortpress_attribute_id", "1");
        channelProperties.put("zigbee_shortpress_attribute_value", "2");
        mockCluster(8);
        boolean initResult = converter.initializeConverter();
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

        converter.initializeConverter();
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

        converter.initializeConverter();
        converter.disposeConverter();

        verify(cluster, times(1)).removeAttributeListener(converter);
        verify(cluster, times(0)).removeCommandListener(any(ZclCommandListener.class));
    }

    @Test
    public void commandWithoutSpecifiedParamIsHandled() {
        channelProperties.put("zigbee_shortpress_cluster_id", "768");
        channelProperties.put("zigbee_shortpress_command_id", "0x01");
        mockCluster(768);
        converter.initializeConverter();

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
        converter.initializeConverter();

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
        converter.initializeConverter();

        ZclAttribute attribute = new ZclAttribute(
                ZclClusterType.MULTISTATE_INPUT__BASIC, 85, "foo",
                ZclDataType.UNSIGNED_16_BIT_INTEGER,
                false, false, true, true);
        attribute.updateValue(1);
        converter.attributeUpdated(attribute);

        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
    }


    @Test
    public void commandWithNonMatchingSpecifiedParamNameIsNotHandled() {
        channelProperties.put("zigbee_shortpress_cluster_id", "768");
        channelProperties.put("zigbee_shortpress_command_id", "0x01");
        channelProperties.put("zigbee_shortpress_parameter_name", "blueMode");
        channelProperties.put("zigbee_shortpress_parameter_value", "1");
        mockCluster(768);
        converter.initializeConverter();

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
        converter.initializeConverter();

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
        converter.initializeConverter();

        ZclAttribute attribute = new ZclAttribute(
                ZclClusterType.MULTISTATE_INPUT__BASIC, 85, "foo",
                ZclDataType.UNSIGNED_16_BIT_INTEGER,
                false, false, true, true);
        attribute.updateValue(2);
        converter.attributeUpdated(attribute);

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
        converter.initializeConverter();

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
        converter.initializeConverter();

        ZclAttribute attribute = new ZclAttribute(
                ZclClusterType.MULTISTATE_INPUT__BASIC, 0x17, "foo",
                ZclDataType.UNSIGNED_16_BIT_INTEGER,
                false, false, true, true);
        attribute.updateValue(2);
        converter.attributeUpdated(attribute);

        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.SHORT_PRESSED);
        verify(thingHandler, times(1)).triggerChannel(channel.getUID(), CommonTriggerEvents.LONG_PRESSED);
        verify(thingHandler, times(0)).triggerChannel(channel.getUID(), CommonTriggerEvents.DOUBLE_PRESSED);
    }

    private ZclCluster mockCluster(int clusterId) {
        ZclCluster cluster = mock(ZclCluster.class);
        when(cluster.getClusterId()).thenReturn(clusterId);
        when(cluster.bind(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(CompletableFuture.completedFuture(new CommandResult(new ZigBeeCommand())));
        when(endpoint.getOutputCluster(clusterId)).thenReturn(cluster);
        when(endpoint.getInputCluster(clusterId)).thenReturn(cluster);
        return cluster;
    }
}
