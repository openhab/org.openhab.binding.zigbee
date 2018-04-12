/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.smarthome.config.core.ConfigDescriptionRegistry;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.thing.type.ThingTypeRegistry;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides methods to match a ZigBee thing to the thing types provided in the
 * {@link ConfigDescriptionRegistry}. The matching will be performed against a set of properties in the
 * {@link ThingType}.
 * <p>
 * Matching rules ->
 * <ul>
 * <li>The matcher requires a match of all properties in the thing-type.
 * <li>The properties to be used for matching can contain extra properties that aren't used. This allows the user to
 * pass in all known properties of the device, and the matcher will use whatever properties are in the thing-type.
 * </ul>
 *
 * @author Chris Jackson - Initial Implementation
 *
 */
public class ZigBeeThingTypeMatcher {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeThingTypeMatcher.class);

    private ThingTypeRegistry thingTypeRegistry;

    private final String THING_MATCHER_PROPERTY = "typeMatcher";

    /**
     * Sets the reference to the {@link ThingTypeRegistry} used to get the list of thing types.
     *
     * @param thingTypeRegistry reference to the {@link ThingTypeRegistry}
     */
    public void setThingTypeRegistry(ThingTypeRegistry thingTypeRegistry) {
        this.thingTypeRegistry = thingTypeRegistry;
    }

    /**
     * Matches a set of properties to a single thing type. If no match is found, null is returned.
     * The matcher checks all registered thing types. If multiple matches are found, null is returned.
     *
     * @param properties a Map of strings containing the properties to match
     * @return the {@link ThingTypeUID} of the matched thing type, or null if no single thing type matched the filter
     *         properties
     */
    public ThingTypeUID matchThingType(Map<String, String> properties) {
        if (thingTypeRegistry == null) {
            return null;
        }

        ThingTypeUID matchedThingType = null;

        for (ThingType thingType : thingTypeRegistry.getThingTypes(ZigBeeBindingConstants.BINDING_ID)) {

            if (propertiesMatchThingType(properties, thingType)) {
                if (matchedThingType != null) {
                    // Don't allow duplicate matches
                    logger.debug("Duplicate match {} and {}", matchedThingType, thingType.getUID());
                    return null;
                }
                matchedThingType = thingType.getUID();
            }
        }

        return matchedThingType;
    }

    private boolean propertiesMatchThingType(Map<String, String> properties, ThingType thingType) {
        Set<RequiredProperty> requiredProperties = getRequiredProperties(thingType.getProperties());

        // Do not consider thing types that do not specify any required properties.
        if (requiredProperties.isEmpty()) {
            return false;
        }

        return requiredProperties.stream()
                .allMatch(requiredProperty -> requiredProperty.value.equals(properties.get(requiredProperty.name)));
    }

    private Set<RequiredProperty> getRequiredProperties(Map<String, String> thingProperties) {
        Set<RequiredProperty> requiredProperties = new HashSet<>();

        for (Entry<String, String> entry : thingProperties.entrySet()) {
            String parts[] = entry.getKey().split(":");
            if (parts.length == 2 && THING_MATCHER_PROPERTY.equals(parts[0])) {
                requiredProperties.add(new RequiredProperty(parts[1], entry.getValue()));
            }
        }

        return requiredProperties;
    }

    private class RequiredProperty {
        public String name;
        public String value;

        public RequiredProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
