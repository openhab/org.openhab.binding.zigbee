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
package org.openhab.binding.zigbee.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;

/**
 *
 * @author Chris Jackson - Initial Implementation
 *
 */
public class ZigBeeThingTypeMatcherTest {

    @Test
    public void testMatcher()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        ZigBeeThingTypeMatcher matcher = new ZigBeeThingTypeMatcher();
        Map<String, Object> properties;

        properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, "Vendor1");
        properties.put(Thing.PROPERTY_MODEL_ID, "Model1");
        matcher.matchThingType(properties);
        assertEquals(new ThingTypeUID("zigbee:type1"), matcher.matchThingType(properties));

        properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, "Vendor1");
        properties.put(Thing.PROPERTY_MODEL_ID, "Model2");
        assertEquals(new ThingTypeUID("zigbee:type3"), matcher.matchThingType(properties));

        // No match as overlapping properties
        properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, "Vendor1");
        properties.put(Thing.PROPERTY_MODEL_ID, "Model2");
        properties.put(Thing.PROPERTY_FIRMWARE_VERSION, "Version4");
        assertEquals(null, matcher.matchThingType(properties));

        // No match as overlapping properties
        properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, "Vendor1");
        assertEquals(null, matcher.matchThingType(properties));

        // No match at all
        properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, "Vendor3");
        assertEquals(null, matcher.matchThingType(properties));
    }
}
