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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeCommand;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;

/**
 * Unit tests for the {@link ZigBeeConverterIasTamper}.
 *
 * @author Tommaso Travaglino - initial contribution
 */
public class ZigBeeConverterIasTamperTest {

    private ZigBeeConverterIasTamper converter;
    private ZigBeeThingHandler thingHandler;
    private Channel channel;
    private ZigBeeCoordinatorHandler coordinatorHandler;
    private ZigBeeEndpoint endpoint;
    private ThingUID thingUID;

    @SuppressWarnings("deprecation")
    @BeforeEach
    public void setup() throws InterruptedException, ExecutionException {
        IeeeAddress ieeeAddress = new IeeeAddress();
        int endpointId = 1;

        thingUID = new ThingUID("bindingId", "thingTypeId", "id");

        ZclCluster zclCluster = mock(ZclIasZoneCluster.class);

        endpoint = mock(ZigBeeEndpoint.class);
        when(endpoint.getEndpointId()).thenReturn(endpointId);
        when(endpoint.getIeeeAddress()).thenReturn(ieeeAddress);
        when(endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID)).thenReturn(zclCluster);

        @SuppressWarnings("unchecked")
        Future<CommandResult> reportingFuture = mock(Future.class);
        when(zclCluster.setReporting(any(), anyInt(), anyInt())).thenReturn(reportingFuture);

        CommandResult reportingResult = new CommandResult(ZigBeeStatus.SUCCESS, new ZigBeeCommand());
        when(reportingFuture.get()).thenReturn(reportingResult);

        channel = mock(Channel.class);
        thingHandler = mock(ZigBeeThingHandler.class);

        coordinatorHandler = mock(ZigBeeCoordinatorHandler.class);
        when(coordinatorHandler.getEndpoint(ieeeAddress, endpointId)).thenReturn(endpoint);

        converter = new ZigBeeConverterIasTamper();
        converter.initialize(channel, coordinatorHandler, ieeeAddress, endpointId);
        converter.initializeConverter(thingHandler);
    }

    @Test
    public void testBitTestIsProperlySet() {
        assertEquals(ZigBeeConverterIas.CIE_TAMPER, converter.bitTest);
    }

    @Test
    public void testEndpointHasIasZoneInputCluster() {
        Channel channel = converter.getChannel(thingUID, endpoint);
        assertNotNull(channel);

        assertTrue(channel.getUID().getId().endsWith(ZigBeeBindingConstants.CHANNEL_NAME_IAS_TAMPER));
        assertEquals(ZigBeeBindingConstants.CHANNEL_IAS_TAMPER, channel.getChannelTypeUID());
        assertEquals(ZigBeeBindingConstants.CHANNEL_LABEL_IAS_TAMPER, channel.getLabel());
    }

    @Test
    public void testEndpointHasNoIasZoneInputCluster() {
        // Change the endpoint mock to return null instead of the cluster
        when(endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID)).thenReturn(null);

        Channel channel = converter.getChannel(thingUID, endpoint);
        assertNull(channel);
    }

}
