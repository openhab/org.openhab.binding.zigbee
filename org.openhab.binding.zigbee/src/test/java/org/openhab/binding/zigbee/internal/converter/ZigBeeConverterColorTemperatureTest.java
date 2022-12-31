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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorModeEnum;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeConverterColorTemperatureTest {

    private ZigBeeConverterColorTemperature getConverter() {
        ZigBeeConverterColorTemperature converter = new ZigBeeConverterColorTemperature();
        try {
            Field fieldMin = ZigBeeConverterColorTemperature.class.getDeclaredField("kelvinMin");
            fieldMin.setAccessible(true);
            fieldMin.set(converter, Integer.valueOf(2000));

            Field fieldMax = ZigBeeConverterColorTemperature.class.getDeclaredField("kelvinMax");
            fieldMax.setAccessible(true);
            fieldMax.set(converter, Integer.valueOf(6500));

            Field fieldRange = ZigBeeConverterColorTemperature.class.getDeclaredField("kelvinRange");
            fieldRange.setAccessible(true);
            fieldRange.set(converter, 4500.0);
        } catch (NoSuchFieldException | SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return converter;
    }

    private int convertPercentToMired(ZigBeeConverterColorTemperature converter, PercentType colorTemp) {
        try {
            Method method = ZigBeeConverterColorTemperature.class.getDeclaredMethod("percentToMired",
                    PercentType.class);
            method.setAccessible(true);

            return (int) method.invoke(converter, colorTemp);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return 0;
        }
    }

    private PercentType convertMiredToPercent(ZigBeeConverterColorTemperature converter, Integer mired) {
        try {
            Method method = ZigBeeConverterColorTemperature.class.getDeclaredMethod("miredToPercent", Integer.class);
            method.setAccessible(true);

            return (PercentType) method.invoke(converter, mired);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

    @Test
    public void testConvertPercentToMired() {
        ZigBeeConverterColorTemperature converter = getConverter();

        assertEquals(153, convertPercentToMired(converter, PercentType.ZERO));
        assertEquals(500, convertPercentToMired(converter, PercentType.HUNDRED));
    }

    @Test
    public void testConvertMiredToPercent() {
        ZigBeeConverterColorTemperature converter = getConverter();

        assertEquals(null, convertMiredToPercent(converter, null));
        assertEquals(null, convertMiredToPercent(converter, 0x0000));
        assertEquals(null, convertMiredToPercent(converter, 0xffff));
        assertEquals(PercentType.HUNDRED, convertMiredToPercent(converter, 500));
        assertEquals(PercentType.ZERO, convertMiredToPercent(converter, 154));
    }

    @Test
    public void testAttributeNotUpdatedWhenColorModeIsNotColorTemperature() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterColorTemperature converter = getConverter();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        ZclAttribute colorModeAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 8, "ColorMode",
                ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, false, false);
        ZclAttribute colorTemperatureAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 7,
                "ColorTemperature", ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, false, false);

        // Update the color mode to CURRENTHUE_AND_CURRENTSATURATION and ensure that the state is set to UNDEF
        colorModeAttribute.updateValue(ColorModeEnum.CURRENT_HUE_AND_CURRENT_SATURATION.getKey());
        converter.attributeUpdated(colorModeAttribute, colorModeAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(UnDefType.UNDEF, stateCapture.getValue());

        // Set the color temperature and ensure that the channel is not set
        colorTemperatureAttribute.updateValue(Integer.valueOf(100));
        converter.attributeUpdated(colorTemperatureAttribute, colorTemperatureAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Update the color mode to CURRENTX_AND_CURRENTY and ensure that the state is set to UNDEF
        colorModeAttribute.updateValue(ColorModeEnum.CURRENT_X_AND_CURRENT_Y.getKey());
        converter.attributeUpdated(colorModeAttribute, colorModeAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(UnDefType.UNDEF, stateCapture.getValue());

        // Set the color temperature and ensure that the channel is not set
        colorTemperatureAttribute.updateValue(Integer.valueOf(100));
        converter.attributeUpdated(colorTemperatureAttribute, colorTemperatureAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(2)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
    }

    @Test
    public void testAttributeUpdatedWhenColorModeIsNull() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterColorTemperature converter = getConverter();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        ZclAttribute colorTemperatureAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 7,
                "ColorTemperature", ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, false, false);

        // Do not initialize the color mode

        // Set the color temperature and ensure that the channel is set
        colorTemperatureAttribute.updateValue(Integer.valueOf(250));
        converter.attributeUpdated(colorTemperatureAttribute, colorTemperatureAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(convertMiredToPercent(converter, 250), stateCapture.getValue());
    }

    @Test
    public void testAttributeUpdatedWhenColorModeIsColorTemperature() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(ArgumentMatchers.any(IeeeAddress.class), ArgumentMatchers.anyInt()))
                .thenReturn(endpoint);

        ZigBeeConverterColorTemperature converter = getConverter();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        Channel channel = ChannelBuilder.create(new ChannelUID("a:b:c:d"), "").build();
        converter.initialize(channel, coordinatorHandler, new IeeeAddress("1234567890ABCDEF"), 1);
        converter.initializeConverter(thingHandler);

        ZclAttribute colorModeAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 8, "ColorMode",
                ZclDataType.ENUMERATION_8_BIT, false, false, false, false);
        ZclAttribute colorTemperatureAttribute = new ZclAttribute(new ZclColorControlCluster(endpoint), 7,
                "ColorTemperature", ZclDataType.UNSIGNED_16_BIT_INTEGER, false, false, false, false);

        // Update the color mode to COLOR_TEMPERATURE and ensure that the state is not set
        colorModeAttribute.updateValue(ColorModeEnum.COLOR_TEMPERATURE.getKey());
        converter.attributeUpdated(colorModeAttribute, colorModeAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(0)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        // Set the color temperature and ensure that the channel is not set
        colorTemperatureAttribute.updateValue(Integer.valueOf(250));
        converter.attributeUpdated(colorTemperatureAttribute, colorTemperatureAttribute.getLastValue());
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());
        assertEquals(convertMiredToPercent(converter, 250), stateCapture.getValue());
    }

}
