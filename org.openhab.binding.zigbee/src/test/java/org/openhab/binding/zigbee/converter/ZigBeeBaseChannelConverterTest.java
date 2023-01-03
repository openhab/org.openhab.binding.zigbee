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
package org.openhab.binding.zigbee.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.ZigBeeConverterSwitchLevel;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.types.Command;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeProfileType;

/**
 * Test of the ZigBeeBaseChannelConverter
 *
 * @author Chris Jackson - Initial contribution
 * @author Thomas Höfer - Moved to different package because of osgified converter factory
 */
public class ZigBeeBaseChannelConverterTest {

    @Test
    public void percentToLevel() {
        ZigBeeBaseChannelConverter converter = new ZigBeeConverterSwitchLevel();

        assertEquals(0, converter.percentToLevel(PercentType.ZERO));
        assertEquals(254, converter.percentToLevel(PercentType.HUNDRED));
    }

    @Test
    public void levelToPercent() {
        ZigBeeBaseChannelConverter converter = new ZigBeeConverterSwitchLevel();

        assertEquals(PercentType.ZERO, converter.levelToPercent(0));
        assertEquals(PercentType.HUNDRED, converter.levelToPercent(254));
    }

    @Test
    public void getDeviceTypeLabel() {
        ZigBeeBaseChannelConverter converter = new ZigBeeConverterSwitchLevel();

        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        Mockito.when(endpoint.getProfileId()).thenReturn(0x104);
        Mockito.when(endpoint.getDeviceId()).thenReturn(1);
        assertEquals("LEVEL_CONTROL_SWITCH", converter.getDeviceTypeLabel(endpoint));

        Mockito.when(endpoint.getProfileId()).thenReturn(ZigBeeProfileType.ZIGBEE_LIGHT_LINK.getKey());

        Mockito.when(endpoint.getDeviceId()).thenReturn(0);
        assertEquals("ZLL_ON_OFF_LIGHT", converter.getDeviceTypeLabel(endpoint));

        Mockito.when(endpoint.getDeviceId()).thenReturn(65535);
        assertEquals("Unknown Device Type FFFF", converter.getDeviceTypeLabel(endpoint));
    }

    @Test
    public void valueToTemperature() {
        ZigBeeBaseChannelConverter converter = new ZigBeeConverterSwitchLevel();

        assertEquals(new QuantityType(12.34, SIUnits.CELSIUS), converter.valueToTemperature(1234));
    }

    @Test
    public void temperatureToValue() {
        ZigBeeBaseChannelConverter converter = new ZigBeeConverterSwitchLevel();

        assertEquals(Integer.valueOf(1234), converter.temperatureToValue(new DecimalType(12.34)));
        assertEquals(Integer.valueOf(1234), converter.temperatureToValue(new QuantityType(12.34, SIUnits.CELSIUS)));
        assertEquals(Integer.valueOf(1235), converter.temperatureToValue(new QuantityType(12.345, SIUnits.CELSIUS)));
        assertEquals(Integer.valueOf(889),
                converter.temperatureToValue(new QuantityType(48.0, ImperialUnits.FAHRENHEIT)));
    }

    @Test
    public void monitorCommandResponse() throws InterruptedException, ExecutionException {
        ZigBeeBaseChannelConverter converter = new ZigBeeConverterSwitchLevel();

        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);

        converter.thing = thingHandler;

        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        converter.endpoint = endpoint;

        Future<CommandResult> result;
        Command command = OnOffType.ON;
        result = null;
        converter.monitorCommandResponse(command, result);

        Mockito.verify(thingHandler, times(0)).setChannelState(ArgumentMatchers.any(), ArgumentMatchers.any());

        List<Future<CommandResult>> results = new ArrayList<>();

        result = Mockito.mock(Future.class);
        CommandResult commandResult = Mockito.mock(CommandResult.class);
        Mockito.when(result.get()).thenReturn(commandResult);

        results.add(result);
        results.add(null);

        Mockito.when(commandResult.isTimeout()).thenReturn(true);
        Mockito.when(commandResult.isError()).thenReturn(false);
        converter.monitorCommandResponse(command, result);
        Mockito.verify(thingHandler, times(0)).setChannelState(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.when(commandResult.isTimeout()).thenReturn(false);
        Mockito.when(commandResult.isError()).thenReturn(true);
        converter.monitorCommandResponse(command, result);
        Mockito.verify(thingHandler, times(0)).setChannelState(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.when(commandResult.isTimeout()).thenReturn(false);
        Mockito.when(commandResult.isError()).thenReturn(false);
        converter.monitorCommandResponse(command, result);
        Mockito.verify(thingHandler, times(1)).setChannelState(ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}
