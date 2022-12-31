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
package org.openhab.binding.zigbee.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.type.ThingType;
import org.openhab.core.thing.type.ThingTypeBuilder;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.ZigBeeStatus;

/**
 * Test of the ZigBeeThingHandler
 *
 * @author Chris Jackson - Initial contribution
 *
 */
public class ZigBeeThingHandlerTest {
    private List<Integer> processClusterList(Collection<Integer> initialClusters, String newClusters)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
            SecurityException {
        Method privateMethod;

        ZigBeeThingHandler handler = new ZigBeeThingHandler(null, null, null);

        privateMethod = ZigBeeThingHandler.class.getDeclaredMethod("processClusterList", Collection.class,
                String.class);
        privateMethod.setAccessible(true);

        return (List<Integer>) privateMethod.invoke(handler, initialClusters, newClusters);
    }

    @Test
    public void testProcessClusterList() throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, SecurityException {
        List<Integer> clusters = new ArrayList<>();

        clusters = processClusterList(clusters, null);
        assertEquals(0, clusters.size());

        clusters = processClusterList(clusters, "");
        assertEquals(0, clusters.size());

        clusters = processClusterList(clusters, ",");
        assertEquals(0, clusters.size());

        clusters = processClusterList(clusters, "123,456");
        assertEquals(2, clusters.size());
        assertTrue(clusters.contains(123));
        assertTrue(clusters.contains(456));

        clusters = processClusterList(clusters, "123,456");
        assertEquals(0, clusters.size());

        clusters = processClusterList(clusters, "123,456");
        assertEquals(2, clusters.size());
        assertTrue(clusters.contains(123));
        assertTrue(clusters.contains(456));

        clusters = processClusterList(clusters, "321,654");
        assertEquals(4, clusters.size());
        assertTrue(clusters.contains(123));
        assertTrue(clusters.contains(456));
        assertTrue(clusters.contains(321));
        assertTrue(clusters.contains(654));
    }

    @Test
    public void testInitializeDeviceWithNoThingProperty()
            throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchFieldException, InterruptedException, ExecutionException {

        testInitializeDevice(null);
    }

    @Test
    public void testInitializeDeviceWithThingPropertyTrue()
            throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchFieldException, InterruptedException, ExecutionException {

        testInitializeDevice(Boolean.TRUE.toString());
    }

    @Test
    public void testInitializeDeviceWithThingPropertyFalse()
            throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchFieldException, InterruptedException, ExecutionException {

        testInitializeDevice(Boolean.FALSE.toString());
    }

    private void testInitializeDevice(String deviceInitializedProperty)
            throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchFieldException, InterruptedException, ExecutionException {

        int endpointId = 1;
        IeeeAddress ieeeAddress = new IeeeAddress("000D6F000C1534C3");
        ThingType thingType = ThingTypeBuilder
                .instance(ZigBeeBindingConstants.BINDING_ID, ZigBeeBindingConstants.ITEM_TYPE_SWITCH, "thingTypeLabel")
                .build();
        ThingUID thingUID = new ThingUID(thingType.getUID(), "thingUID");

        ZigBeeNode zigBeeNode = mockZigBeeNode(endpointId, ieeeAddress);
        ZigBeeCoordinatorHandler zigBeeCoordinatorHandler = mockZigBeeCoordinatorHandler(zigBeeNode);

        // create the thing properties which can contain the device initialized property
        Map<String, String> thingProperties = new HashMap<String, String>();
        if (deviceInitializedProperty != null && !deviceInitializedProperty.isBlank()) {
            thingProperties.put(ZigBeeBindingConstants.THING_PROPERTY_DEVICE_INITIALIZED, deviceInitializedProperty);
        }

        Channel channel = mockChannel(endpointId, thingUID);
        Thing thing = mockThing(thingType, thingProperties, channel);
        ZigBeeBaseChannelConverter zigBeeChannelConverter = mockZigBeeBaseChannelConverterSuccessfull();

        ZigBeeChannelConverterFactory zigBeeChannelConverterFactory = mockZigBeeChannelConverterFactory(
                zigBeeChannelConverter);

        ZigBeeIsAliveTracker zigBeeIsAliveTracker = mock(ZigBeeIsAliveTracker.class);

        ZigBeeThingHandler zigBeeThingHandler = new ZigBeeThingHandler(thing, zigBeeChannelConverterFactory,
                zigBeeIsAliveTracker);
        injectIntoPrivateField(zigBeeThingHandler, zigBeeCoordinatorHandler, "coordinatorHandler");
        injectIntoPrivateField(zigBeeThingHandler, ieeeAddress, "nodeIeeeAddress");

        // call doNodeInitialisation by reflection as it is not accessible
        Method doNodeInitialisationMethod = ZigBeeThingHandler.class.getDeclaredMethod("doNodeInitialisation",
                (Class<Object>[]) null);
        doNodeInitialisationMethod.setAccessible(true);
        doNodeInitialisationMethod.invoke(zigBeeThingHandler, (Object[]) null);

        // When the device was already initialized the initialization will be skipped and the thing property will not
        // be updated
        if (Boolean.TRUE.toString().equals(deviceInitializedProperty)) {
            Mockito.verify(zigBeeChannelConverter, never()).initializeDevice();
            Mockito.verify(thing, never()).setProperty(ZigBeeBindingConstants.THING_PROPERTY_DEVICE_INITIALIZED,
                    Boolean.TRUE.toString());
        } else {
            Mockito.verify(zigBeeChannelConverter, times(1)).initializeDevice();
            Mockito.verify(thing, times(1)).setProperty(ZigBeeBindingConstants.THING_PROPERTY_DEVICE_INITIALIZED,
                    Boolean.TRUE.toString());
        }
    }

    private ZigBeeChannelConverterFactory mockZigBeeChannelConverterFactory(
            ZigBeeBaseChannelConverter zigBeeChannelConverter) {
        ZigBeeChannelConverterFactory zigBeeChannelConverterFactory = mock(ZigBeeChannelConverterFactory.class);
        when(zigBeeChannelConverterFactory.createConverter(any(Channel.class), any(ZigBeeCoordinatorHandler.class),
                any(IeeeAddress.class), any(int.class))).thenReturn(zigBeeChannelConverter);

        return zigBeeChannelConverterFactory;
    }

    private ZigBeeBaseChannelConverter mockZigBeeBaseChannelConverterSuccessfull() {
        ZigBeeBaseChannelConverter zigBeeChannelConverter = mock(ZigBeeBaseChannelConverter.class);
        when(zigBeeChannelConverter.initializeDevice()).thenReturn(true);
        when(zigBeeChannelConverter.initializeConverter(any(ZigBeeThingHandler.class))).thenReturn(true);

        return zigBeeChannelConverter;
    }

    private Thing mockThing(ThingType thingType, Map<String, String> thingProperties, Channel channel) {
        List<Channel> channels = new ArrayList<Channel>(Arrays.asList(channel));

        Thing thing = mock(Thing.class);
        when(thing.getThingTypeUID()).thenReturn(thingType.getUID());
        when(thing.getProperties()).thenReturn(thingProperties);
        when(thing.getChannels()).thenReturn(channels);
        when(thing.getChannel(channel.getUID().getAsString())).thenReturn(channel);

        return thing;
    }

    private Channel mockChannel(int endpointId, ThingUID thingUID) {
        Map<String, String> channelProperties = new HashMap<String, String>();
        channelProperties.put(ZigBeeBindingConstants.CHANNEL_PROPERTY_ENDPOINT, Integer.toString(endpointId));

        ChannelUID channelUID = new ChannelUID(thingUID, "channelUID");
        Channel channel = mock(Channel.class);
        when(channel.getUID()).thenReturn(channelUID);
        when(channel.getProperties()).thenReturn(channelProperties);
        when(channel.getConfiguration()).thenReturn(new Configuration());

        return channel;
    }

    private ZigBeeCoordinatorHandler mockZigBeeCoordinatorHandler(ZigBeeNode zigBeeNode) {
        ZigBeeCoordinatorHandler zigBeeCoordinatorHandler = mock(ZigBeeCoordinatorHandler.class);
        when(zigBeeCoordinatorHandler.getNode(any(IeeeAddress.class))).thenReturn(zigBeeNode);
        return zigBeeCoordinatorHandler;
    }

    private ZigBeeNode mockZigBeeNode(int endpointId, IeeeAddress ieeeAddress)
            throws InterruptedException, ExecutionException {

        ZigBeeEndpoint zigbeeEndpoint = mock(ZigBeeEndpoint.class);
        Future<ZigBeeStatus> updateBindingTableFuture = mock(Future.class);
        when(updateBindingTableFuture.get()).thenReturn(ZigBeeStatus.SUCCESS);

        ZigBeeNode zigBeeNode = mock(ZigBeeNode.class);
        when(zigBeeNode.isDiscovered()).thenReturn(true);
        when(zigBeeNode.getEndpoint(endpointId)).thenReturn(zigbeeEndpoint);
        when(zigBeeNode.getIeeeAddress()).thenReturn(ieeeAddress);
        when(zigBeeNode.updateBindingTable()).thenReturn(updateBindingTableFuture);

        return zigBeeNode;
    }

    private void injectIntoPrivateField(Object object, Object value, String fieldname)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field coordinatorHandlerField = ZigBeeThingHandler.class.getDeclaredField(fieldname);
        coordinatorHandlerField.setAccessible(true);
        coordinatorHandlerField.set(object, value);
    }

}
