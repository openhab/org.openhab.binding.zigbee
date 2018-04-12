/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.junit.Test;

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
        Map<String, String> properties;

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
