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
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.zigbee.converter.warningdevice.SoundLevel;
import org.openhab.binding.zigbee.converter.warningdevice.SquawkMode;
import org.openhab.binding.zigbee.converter.warningdevice.SquawkType;
import org.openhab.binding.zigbee.converter.warningdevice.WarningMode;
import org.openhab.binding.zigbee.converter.warningdevice.WarningType;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;

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

    @BeforeEach
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
        converter.initialize(channel, coordinatorHandler, ieeeAddress, endpointId);

        assertTrue(converter.initializeConverter(thingHandler));
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
        verify(cluster).squawk(0b01000001);
    }

    @Test
    public void testHandleUnknownCommand() {
        converter.handleCommand(OnOffType.OFF);
        verify(cluster, never()).startWarningCommand(any(Integer.class), any(Integer.class));
    }

}
