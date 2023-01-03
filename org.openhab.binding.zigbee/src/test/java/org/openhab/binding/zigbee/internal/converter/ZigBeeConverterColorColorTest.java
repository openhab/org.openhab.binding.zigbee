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
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.ConfigDescriptionParameter.Type;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorModeEnum;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Tests for color converter.
 * Note that the converter uses a timer to ensure consistency of color updates, so tests of color updates will need to
 * account for this!
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeConverterColorColorTest {

    @Test
    public void testInitialisation() throws InterruptedException, ExecutionException {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterColorColor converter = new ZigBeeConverterColorColor();
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();

        ZclOnOffCluster onoffCluster = Mockito.mock(ZclOnOffCluster.class);
        ZclColorControlCluster colorCluster = Mockito.mock(ZclColorControlCluster.class);
        ZclLevelControlCluster levelCluster = Mockito.mock(ZclLevelControlCluster.class);
        Mockito.when(endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID)).thenReturn(onoffCluster);
        Mockito.when(endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID)).thenReturn(colorCluster);
        Mockito.when(endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID)).thenReturn(levelCluster);

        Future<Boolean> attributeFuture = mock(Future.class);
        when(attributeFuture.get()).thenReturn(false);

        Mockito.when(onoffCluster.discoverAttributes(ArgumentMatchers.anyBoolean())).thenReturn(attributeFuture);
        Mockito.when(colorCluster.discoverAttributes(ArgumentMatchers.anyBoolean())).thenReturn(attributeFuture);
        Mockito.when(levelCluster.discoverAttributes(ArgumentMatchers.anyBoolean())).thenReturn(attributeFuture);

        Mockito.when(onoffCluster.isAttributeSupported(ArgumentMatchers.anyInt())).thenReturn(false);
        Mockito.when(colorCluster.isAttributeSupported(ArgumentMatchers.anyInt())).thenReturn(false);
        Mockito.when(levelCluster.isAttributeSupported(ArgumentMatchers.anyInt())).thenReturn(false);

        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        List<ConfigDescriptionParameter> config = converter.getConfigDescription();
        assertEquals(4, config.size());

        Map<String, ConfigDescriptionParameter> parameterMap = new HashMap<>();
        Iterator<ConfigDescriptionParameter> iterator = config.iterator();
        while (iterator.hasNext()) {
            ConfigDescriptionParameter configParameter = iterator.next();
            parameterMap.put(configParameter.getName(), configParameter);
        }

        assertTrue(parameterMap.containsKey("zigbee_color_controlmethod"));
        ConfigDescriptionParameter configParameter = parameterMap.get("zigbee_color_controlmethod");
        assertEquals("AUTO", configParameter.getDefault());
        assertEquals(3, configParameter.getOptions().size());
        assertEquals(Type.TEXT, configParameter.getType());
    }

    @Test
    public void testAttributeUpdated() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterColorColor converter = new ZigBeeConverterColorColor();
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
        // Note that the converter assumes the default HSB is 0,0,100, so this is returned first.
        onAttribute.updateValue(Boolean.TRUE);
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(new HSBType("0,0,100"), stateCapture.getValue());

        // Set the level to ensure that when no OnOff has been received, state updates are made
        levelAttribute.updateValue(Integer.valueOf(50));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new HSBType("0,0,20"), stateCapture.getValue());

        // Turn OFF, and state should be OFF (indeed 0,0,0)
        onAttribute.updateValue(Boolean.FALSE);
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
        assertEquals(new HSBType("0,0,0"), stateCapture.getValue());

        // No update here since state is OFF, but we remember the level (20%)...
        levelAttribute.updateValue(Integer.valueOf(101));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(3)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // When turned ON, we are set to the last level
        onAttribute.updateValue(Boolean.TRUE);
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(4)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new HSBType("0,0,40"), stateCapture.getValue());

        // Set the level and ensure it updates
        levelAttribute.updateValue(Integer.valueOf(50));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(5)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(new HSBType("0,0,20"), stateCapture.getValue());
    }

    @Test
    public void testAttributeNotUpdatedWhenColorModeIsColorTemperature() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterColorColor converter = new ZigBeeConverterColorColor();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        ZclAttribute colorModeAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 8, "ColorMode",
                ZclDataType.ENUMERATION_8_BIT, false, false, false, false);
        ZclAttribute onAttribute = new ZclAttribute(new ZclOnOffCluster(endpoint), 0, "OnOff", ZclDataType.BOOLEAN,
                false, false, false, false);
        ZclAttribute levelAttribute = new ZclAttribute(new ZclLevelControlCluster(endpoint), 0, "Level",
                ZclDataType.BOOLEAN, false, false, false, false);
        ZclAttribute currentHueAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 0, "CurrentHue",
                ZclDataType.UNSIGNED_8_BIT_INTEGER, false, false, false, false);
        ZclAttribute currentSaturationAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 1,
                "CurrentSaturation", ZclDataType.UNSIGNED_8_BIT_INTEGER, false, false, false, false);
        ZclAttribute currentXAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 3, "CurrentX",
                ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, false, false);
        ZclAttribute currentYAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 4, "CurrentY",
                ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, false, false);

        // Update the color mode to COLOR_TEMPERATURE and ensure that the state is set to UNDEF
        colorModeAttribute.updateValue(ColorModeEnum.COLOR_TEMPERATURE.getKey());
        converter.attributeUpdated(colorModeAttribute, colorModeAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(UnDefType.UNDEF, stateCapture.getValue());

        // Turn ON and ensure that the channel is not set
        onAttribute.updateValue(Boolean.TRUE);
        converter.attributeUpdated(onAttribute, onAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the level and ensure that the channel is not set
        levelAttribute.updateValue(Integer.valueOf(50));
        converter.attributeUpdated(levelAttribute, levelAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the hue and ensure that the channel is not set
        currentHueAttribute.updateValue(Integer.valueOf(10));
        converter.attributeUpdated(currentHueAttribute, currentHueAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the saturation and ensure that the channel is not set
        currentSaturationAttribute.updateValue(Integer.valueOf(10));
        converter.attributeUpdated(currentSaturationAttribute, currentSaturationAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the currentX and ensure that the channel is not set
        currentXAttribute.updateValue(Integer.valueOf(10));
        converter.attributeUpdated(currentXAttribute, currentXAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the level and ensure that the channel is not set
        currentYAttribute.updateValue(Integer.valueOf(10));
        converter.attributeUpdated(currentYAttribute, currentYAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
    }

    @Disabled
    @Test
    public void initializeDevicee() throws InterruptedException, ExecutionException {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterColorColor converter = new ZigBeeConverterColorColor();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);

        ZclColorControlCluster colorCluster = Mockito.mock(ZclColorControlCluster.class);
        Mockito.when(endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID)).thenReturn(colorCluster);

        Future<CommandResult> futureGet = Mockito.mock(Future.class);
        CommandResult resultGet = Mockito.mock(CommandResult.class);
        Mockito.when(futureGet.get()).thenReturn(resultGet);
        Mockito.when(resultGet.isSuccess()).thenReturn(true);
        Mockito.when(colorCluster.bind(ArgumentMatchers.any(), ArgumentMatchers.anyInt())).thenReturn(futureGet);

        Future<Boolean> futureDiscover = Mockito.mock(Future.class);
        Mockito.when(futureDiscover.get()).thenReturn(false);

        Mockito.when(colorCluster.discoverAttributes(false)).thenReturn(futureDiscover);

        converter.initializeDevice();
    }

}
