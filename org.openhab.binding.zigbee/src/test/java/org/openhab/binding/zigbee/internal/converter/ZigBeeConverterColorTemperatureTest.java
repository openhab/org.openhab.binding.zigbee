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
}
