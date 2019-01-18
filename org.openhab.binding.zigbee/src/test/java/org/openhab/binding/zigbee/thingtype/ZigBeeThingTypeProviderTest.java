/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.thingtype;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.junit.Before;
import org.junit.Test;
import org.openhab.binding.zigbee.internal.ZigBeeHandlerFactory;

/**
 * Testing the {@link ZigBeeThingTypeProvider} provisioning process.
 *
 * @author Thomas Höfer - Initial contribution
 */
public class ZigBeeThingTypeProviderTest {

    private static final String BINDING = "zigbee";
    private static final ThingTypeUID UID1 = new ThingTypeUID(BINDING, "tt1");
    private static final ThingTypeUID UID2 = new ThingTypeUID(BINDING, "tt2");
    private static final ThingTypeUID UID3 = new ThingTypeUID(BINDING, "tt3");

    private final ZigBeeThingTypeProvider provider1 = createProvider(UID1, UID2);
    private final ZigBeeThingTypeProvider provider2 = createProvider(UID3);

    private ZigBeeHandlerFactory factory;

    @Before
    public void setup() {
        factory = new ZigBeeHandlerFactory();
    }

    @Test
    public void testNoProviders() {
        assertFalse(factory.supportsThingType(UID1));
    }

    @Test
    public void testAddThingTypes() {
        factory.addZigBeeThingTypeProvider(provider1);

        assertTrue(factory.supportsThingType(UID1));
        assertTrue(factory.supportsThingType(UID2));
        assertFalse(factory.supportsThingType(UID3));
    }

    @Test
    public void testRemoveThingTypes() {
        factory.addZigBeeThingTypeProvider(provider1);
        factory.removeZigBeeThingTypeProvider(provider1);

        assertFalse(factory.supportsThingType(UID1));
        assertFalse(factory.supportsThingType(UID2));
        assertFalse(factory.supportsThingType(UID3));
    }

    @Test
    public void testSeveralProviders() {
        factory.addZigBeeThingTypeProvider(provider1);

        assertTrue(factory.supportsThingType(UID1));
        assertTrue(factory.supportsThingType(UID2));
        assertFalse(factory.supportsThingType(UID3));

        factory.addZigBeeThingTypeProvider(provider2);

        assertTrue(factory.supportsThingType(UID1));
        assertTrue(factory.supportsThingType(UID2));
        assertTrue(factory.supportsThingType(UID3));

        factory.removeZigBeeThingTypeProvider(provider1);

        assertFalse(factory.supportsThingType(UID1));
        assertFalse(factory.supportsThingType(UID2));
        assertTrue(factory.supportsThingType(UID3));

        factory.removeZigBeeThingTypeProvider(provider2);

        assertFalse(factory.supportsThingType(UID1));
        assertFalse(factory.supportsThingType(UID2));
        assertFalse(factory.supportsThingType(UID3));
    }

    private ZigBeeThingTypeProvider createProvider(final ThingTypeUID... thingTypeUIDs) {
        return new ZigBeeThingTypeProvider() {
            @Override
            public @NonNull Set<@NonNull ThingTypeUID> getThingTypeUIDs() {
                return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(thingTypeUIDs)));
            }
        };
    }
}
