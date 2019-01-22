/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import static org.junit.Assert.*;

import java.time.Duration;

import org.junit.Test;
import org.openhab.binding.zigbee.internal.converter.warningdevice.SirenLevel;
import org.openhab.binding.zigbee.internal.converter.warningdevice.WarningMode;
import org.openhab.binding.zigbee.internal.converter.warningdevice.WarningType;

/**
 * Unit tests for {@link WarningType}.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class WarningTypeTest {

    @Test
    public void testSerializeWarningType() {
        WarningType warningType = new WarningType(true, WarningMode.FIRE, SirenLevel.VERY_HIGH, Duration.ofSeconds(30));
        String commandString = warningType.serializeToCommand();
        assertEquals("useStrobe=true warningMode=FIRE sirenLevel=VERY_HIGH duration=PT30S", commandString);
    }

    @Test
    public void testParseCommand() {
        WarningType warningType = WarningType
                .parse("useStrobe=true warningMode=FIRE sirenLevel=VERY_HIGH duration=PT30S");
        assertEquals(Duration.ofSeconds(30), warningType.getDuration());
        assertEquals(SirenLevel.VERY_HIGH, warningType.getSirenLevel());
        assertEquals(WarningMode.FIRE, warningType.getWarningMode());
        assertTrue(warningType.isUseStrobe());
    }

    @Test
    public void testParseCommandDefaults() {
        WarningType warningType = WarningType.parse("");
        assertEquals(Duration.ofSeconds(15), warningType.getDuration());
        assertEquals(SirenLevel.HIGH, warningType.getSirenLevel());
        assertEquals(WarningMode.BURGLAR, warningType.getWarningMode());
        assertTrue(warningType.isUseStrobe());
    }

}
