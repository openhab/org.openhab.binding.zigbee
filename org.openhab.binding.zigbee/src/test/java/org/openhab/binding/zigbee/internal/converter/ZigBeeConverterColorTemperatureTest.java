/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.eclipse.smarthome.core.library.types.PercentType;
import org.junit.Test;

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

    private int convertPercentToKelvin(ZigBeeConverterColorTemperature converter, PercentType colorTemp) {
        try {
            Method method = ZigBeeConverterColorTemperature.class.getDeclaredMethod("convertPercentToKelvin",
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

    private PercentType convertKelvinToPercent(ZigBeeConverterColorTemperature converter, Integer kelvin) {
        try {
            Method method = ZigBeeConverterColorTemperature.class.getDeclaredMethod("convertKelvinToPercent",
                    Integer.class);
            method.setAccessible(true);

            return (PercentType) method.invoke(converter, kelvin);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

    @Test
    public void testConvertPercentToKelvin() {
        ZigBeeConverterColorTemperature converter = getConverter();

        assertEquals(500, convertPercentToKelvin(converter, PercentType.ZERO));
        assertEquals(154, convertPercentToKelvin(converter, PercentType.HUNDRED));
    }

    @Test
    public void testConvertKelvinToPercent() {
        ZigBeeConverterColorTemperature converter = getConverter();

        assertEquals(null, convertKelvinToPercent(converter, null));
        assertEquals(null, convertKelvinToPercent(converter, 0x0000));
        assertEquals(null, convertKelvinToPercent(converter, 0xffff));
        assertEquals(PercentType.ZERO, convertKelvinToPercent(converter, 500));
        assertEquals(PercentType.HUNDRED, convertKelvinToPercent(converter, 154));
    }
}
