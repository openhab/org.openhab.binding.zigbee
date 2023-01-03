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

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.openhab.binding.zigbee.converter.warningdevice.SoundLevel;
import org.openhab.binding.zigbee.converter.warningdevice.WarningMode;
import org.openhab.binding.zigbee.converter.warningdevice.WarningType;

/**
 * Unit tests for {@link WarningType}.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class WarningTypeTest {

    @Test
    public void testSerializeWarningType() {
        WarningType warningType = new WarningType(true, WarningMode.FIRE.getValue(), SoundLevel.VERY_HIGH.getValue(),
                Duration.ofSeconds(30));
        String commandString = warningType.serializeToCommand();
        assertEquals("type=warning useStrobe=true warningMode=2 sirenLevel=3 duration=PT30S", commandString);
    }

    @Test
    public void testParseCommand() {
        WarningType warningType = WarningType
                .parse("type=warning useStrobe=true warningMode=FIRE sirenLevel=VERY_HIGH duration=PT30S");
        assertEquals(Duration.ofSeconds(30), warningType.getDuration());
        assertEquals(SoundLevel.VERY_HIGH.getValue(), warningType.getSirenLevel());
        assertEquals(WarningMode.FIRE.getValue(), warningType.getWarningMode());
        assertTrue(warningType.isUseStrobe());
    }

    @Test
    public void testParseCommandDefaults() {
        WarningType warningType = WarningType.parse("type=warning");
        assertEquals(Duration.ofSeconds(15), warningType.getDuration());
        assertEquals(SoundLevel.HIGH.getValue(), warningType.getSirenLevel());
        assertEquals(WarningMode.BURGLAR.getValue(), warningType.getWarningMode());
        assertTrue(warningType.isUseStrobe());
    }

}
