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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.openhab.binding.zigbee.converter.warningdevice.SoundLevel;
import org.openhab.binding.zigbee.converter.warningdevice.SquawkMode;
import org.openhab.binding.zigbee.converter.warningdevice.SquawkType;

/**
 * Unit tests for {@link SquawkType}.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class SquawkTypeTest {

    @Test
    public void testSerializeSquawkType() {
        SquawkType squawkType = new SquawkType(true, SquawkMode.DISARMED.getValue(), SoundLevel.VERY_HIGH.getValue());
        String commandString = squawkType.serializeToCommand();
        assertEquals("type=squawk useStrobe=true squawkMode=1 squawkLevel=3", commandString);
    }

    @Test
    public void testParseCommand() {
        SquawkType squawkType = SquawkType
                .parse("type=squawk useStrobe=true squawkMode=DISARMED squawkLevel=VERY_HIGH");
        assertEquals(SoundLevel.VERY_HIGH.getValue(), squawkType.getSquawkLevel());
        assertEquals(SquawkMode.DISARMED.getValue(), squawkType.getSquawkMode());
        assertTrue(squawkType.isUseStrobe());
    }

    @Test
    public void testParseCommandDefaults() {
        SquawkType squawkType = SquawkType.parse("type=squawk");
        assertEquals(SoundLevel.HIGH.getValue(), squawkType.getSquawkLevel());
        assertEquals(SquawkMode.ARMED.getValue(), squawkType.getSquawkMode());
        assertTrue(squawkType.isUseStrobe());
    }

}
