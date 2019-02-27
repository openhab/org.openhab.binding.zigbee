/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.junit.Before;
import org.junit.Test;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasWdCluster;

/**
 * Unit tests for the {@link ZigBeeConverterWarningDevice}.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class ZigBeeConverterWarningDeviceTest {

    private ZigBeeConverterWarningDevice converter;

    private ZigBeeThingHandler thingHandler;
    private Channel channel;
    private ZigBeeCoordinatorHandler coordinatorHandler;
    private ZigBeeEndpoint endpoint;

    private ZclIasWdCluster cluster;

    @Before
    public void setup() {
        IeeeAddress ieeeAddress = new IeeeAddress();
        int endpointId = 1;

        endpoint = mock(ZigBeeEndpoint.class);
        channel = mock(Channel.class);
        thingHandler = mock(ZigBeeThingHandler.class);
        cluster = mock(ZclIasWdCluster.class);

        coordinatorHandler = mock(ZigBeeCoordinatorHandler.class);
        when(coordinatorHandler.getEndpoint(ieeeAddress, endpointId)).thenReturn(endpoint);
        when(endpoint.getInputCluster(ZclIasWdCluster.CLUSTER_ID)).thenReturn(cluster);

        converter = new ZigBeeConverterWarningDevice();
        converter.initialize(thingHandler, channel, coordinatorHandler, ieeeAddress, endpointId);

        assertTrue(converter.initializeConverter());
    }

    @Test
    public void testHandleCommandBurglarVeryHighStrobe() {
        WarningType warningType = new WarningType(true, WarningMode.BURGLAR.getValue(), SoundLevel.VERY_HIGH.getValue(),
                Duration.ofSeconds(50));
        converter.handleCommand(new StringType(warningType.serializeToCommand()));

        // Warning header is composed of: (a) siren level, (b) strobe, (3) warning mode
        // In our case: siren level very_high: 3=0b11; strobe true: 1=0b01; warning mode burglar: 1=0b0001
        verify(cluster).startWarningCommand(0b11010001, 50);
    }

    @Test
    public void testHandleCommandEmergencyPanicMediumNoStrobe() {
        WarningType warningType = new WarningType(false, WarningMode.EMERGENCY_PANIC.getValue(),
                SoundLevel.MEDIUM.getValue(), Duration.ofMinutes(2));
        converter.handleCommand(new StringType(warningType.serializeToCommand()));

        // Warning header is composed of: (a) siren level, (b) strobe, (3) warning mode
        // In our case: siren level medium: 1=0b01; strobe false: 0=0b00; warning mode emergency panic: 6=0b0110
        verify(cluster).startWarningCommand(0b01000110, 2 * 60);
    }

    @Test
    public void testHandleSquawk() {
        SquawkType squawkType = new SquawkType(false, SquawkMode.DISARMED.getValue(), SoundLevel.MEDIUM.getValue());
        converter.handleCommand(new StringType(squawkType.serializeToCommand()));

        // Squawk header is composed of: (a) squawk level, (b) strobe, (3) warning mode
        // In our case: squawk level medium: 1=0b01; strobe false: 0=0b00; squawk mode disarmed: 6=0b0001
        verify(cluster).squawkCommand(0b01000001);
    }

    @Test
    public void testHandleUnknownCommand() {
        converter.handleCommand(OnOffType.OFF);
        verify(cluster, never()).startWarningCommand(any(Integer.class), any(Integer.class));
    }

}
