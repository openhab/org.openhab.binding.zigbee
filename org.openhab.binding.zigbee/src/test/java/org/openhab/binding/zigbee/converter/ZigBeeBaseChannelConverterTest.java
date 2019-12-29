/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import static org.junit.Assert.assertEquals;

import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.junit.Test;
import org.openhab.binding.zigbee.internal.converter.ZigBeeConverterSwitchLevel;

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

}
