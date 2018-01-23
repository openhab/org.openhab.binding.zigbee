/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import static org.junit.Assert.assertEquals;

import org.eclipse.smarthome.core.library.types.PercentType;
import org.junit.Test;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.converter.ZigBeeConverterSwitchLevel;

/**
 * Test of the ZigBeeBaseChannelConverter
 *
 * @author Chris Jackson - Initial contribution
 *
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
}
