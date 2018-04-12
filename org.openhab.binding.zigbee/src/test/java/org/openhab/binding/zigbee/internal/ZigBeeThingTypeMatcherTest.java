package org.openhab.binding.zigbee.internal;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.thing.type.ThingTypeBuilder;
import org.eclipse.smarthome.core.thing.type.ThingTypeRegistry;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;

/**
 *
 * @author Chris Jackson - Initial Implementation
 *
 */
public class ZigBeeThingTypeMatcherTest {
    private final String THING_MATCHER_PROPERTY = "typeMatcher";

    @Test
    public void testMatcher() {
        ZigBeeThingTypeMatcher matcher = new ZigBeeThingTypeMatcher();
        Map<String, String> properties;

        List<ThingType> things = new ArrayList<>();
        properties = new HashMap<>();
        things.add(ThingTypeBuilder.instance(ZigBeeBindingConstants.BINDING_ID, "type0", "Label 0")
                .withProperties(properties).build());
        properties = new HashMap<>();
        properties.put(THING_MATCHER_PROPERTY + ":" + Thing.PROPERTY_VENDOR, "Vendor1");
        properties.put(THING_MATCHER_PROPERTY + ":" + Thing.PROPERTY_MODEL_ID, "Model1");
        things.add(ThingTypeBuilder.instance(ZigBeeBindingConstants.BINDING_ID, "type1", "Label 1")
                .withProperties(properties).build());
        properties = new HashMap<>();
        properties.put(THING_MATCHER_PROPERTY + ":" + Thing.PROPERTY_VENDOR, "Vendor2");
        properties.put(THING_MATCHER_PROPERTY + ":" + Thing.PROPERTY_MODEL_ID, "Model2");
        things.add(ThingTypeBuilder.instance(ZigBeeBindingConstants.BINDING_ID, "type2", "Label 2")
                .withProperties(properties).build());
        properties = new HashMap<>();
        properties.put(THING_MATCHER_PROPERTY + ":" + Thing.PROPERTY_VENDOR, "Vendor1");
        properties.put(THING_MATCHER_PROPERTY + ":" + Thing.PROPERTY_MODEL_ID, "Model2");
        things.add(ThingTypeBuilder.instance(ZigBeeBindingConstants.BINDING_ID, "type3", "Label 3")
                .withProperties(properties).build());
        properties = new HashMap<>();
        properties.put(THING_MATCHER_PROPERTY + ":" + Thing.PROPERTY_VENDOR, "Vendor1");
        properties.put(THING_MATCHER_PROPERTY + ":" + Thing.PROPERTY_MODEL_ID, "Model2");
        properties.put(THING_MATCHER_PROPERTY + ":" + Thing.PROPERTY_FIRMWARE_VERSION, "Version4");
        things.add(ThingTypeBuilder.instance(ZigBeeBindingConstants.BINDING_ID, "type4", "Label 4")
                .withProperties(properties).build());

        ThingTypeRegistry registry = Mockito.mock(ThingTypeRegistry.class);
        Mockito.when(registry.getThingTypes(Mockito.anyString())).thenReturn(things);

        matcher.setThingTypeRegistry(registry);

        properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, "Vendor1");
        properties.put(Thing.PROPERTY_MODEL_ID, "Model1");
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
