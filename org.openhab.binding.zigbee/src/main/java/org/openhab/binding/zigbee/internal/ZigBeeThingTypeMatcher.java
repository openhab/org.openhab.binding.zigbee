/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.ConfigDescriptionRegistry;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
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
 * <p>
 * The property file is a simple text file with the thingtypeUID at the beginning of the line, followed by a set of
 * comma separate attributes.
 * eg.
 * <ul>
 * <li>philips_sml001,vendor=Philips,modelId=SML001
 * <li>smartthings_motionv4,vendor=SmartThings,modelId=motionv4
 * </ul>
 *
 * @author Chris Jackson - Initial Implementation
 * @author Thomas Höfer - Refactored class to provide only one single instance
 */
public class ZigBeeThingTypeMatcher {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeThingTypeMatcher.class);

    private final Map<String, List<RequiredProperty>> discoveryProperties = new HashMap<>();

    private static final String DISCOVERY_PROPERTIES_FILE = "/discovery.txt";

    private static final ZigBeeThingTypeMatcher MATCHER = new ZigBeeThingTypeMatcher();

    private ZigBeeThingTypeMatcher() {
        // use the getInstance operation to get the single instance of this class
    }

    /**
     * Provides the instance of this thing type matcher.
     *
     * @return the instance of this matcher
     */
    public static synchronized ZigBeeThingTypeMatcher getInstance() {
        return MATCHER;
    }

    /**
     * Matches a set of properties to a single thing type. If no match is found, null is returned.
     * The matcher checks all registered thing types. If multiple matches are found, null is returned.
     *
     * @param properties a Map of strings containing the properties to match
     * @return the {@link ThingTypeUID} of the matched thing type, or null if no single thing type matched the filter
     *         properties
     */
    public ThingTypeUID matchThingType(Map<String, Object> properties) {
        // Only create the index once
        try {
            readDiscoveryProperties();
        } catch (IOException exception) {
            logger.debug("IOException reading ZigBee discovery properties", exception);
            return null;
        }

        String matchedThingType = null;

        for (Entry<String, List<RequiredProperty>> discoveryThing : discoveryProperties.entrySet()) {

            if (discoveryThing.getValue().stream().allMatch(
                    requiredProperty -> requiredProperty.value.equals(properties.get(requiredProperty.name)))) {
                if (matchedThingType != null) {
                    // Don't allow duplicate matches
                    logger.debug("Duplicate match {} and {}", matchedThingType, discoveryThing.getKey());
                    return null;
                }
                matchedThingType = discoveryThing.getKey();
            }
        }

        if (matchedThingType == null) {
            return null;
        }

        return new ThingTypeUID(ZigBeeBindingConstants.BINDING_ID, matchedThingType);
    }

    private synchronized void readDiscoveryProperties() throws IOException {
        if (!discoveryProperties.isEmpty()) {
            return;
        }

        InputStream input = getClass().getResourceAsStream(DISCOVERY_PROPERTIES_FILE);
        if (input == null) {
            return;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));

        while (reader.ready()) {
            String line = reader.readLine();

            String[] elements = line.split(",");
            if (elements.length < 2) {
                continue;
            }

            List<RequiredProperty> newProperties = new ArrayList<>();
            for (String element : elements) {
                String discoveryElement[] = element.split("=");
                if (discoveryElement.length != 2) {
                    continue;
                }

                newProperties.add(new RequiredProperty(discoveryElement[0].trim(), discoveryElement[1].trim()));
            }

            if (newProperties.isEmpty()) {
                continue;
            }

            discoveryProperties.put(elements[0], newProperties);
        }

        reader.close();

        return;
    }

    /**
     * Gets a list of thing types supported by this binding
     *
     * @return {@link Set} of supported {@link ThingTypeUID}.
     */
    public @NonNull Set<@NonNull ThingTypeUID> getSupportedThingTypeUIDs() {
        try {
            readDiscoveryProperties();
        } catch (IOException exception) {
            logger.debug("IOException reading ZigBee discovery properties", exception);
            return Collections.emptySet();
        }

        Set<@NonNull ThingTypeUID> thingTypes = new HashSet<>();
        for (Entry<String, List<RequiredProperty>> thingProperties : discoveryProperties.entrySet()) {
            thingTypes.add(new ThingTypeUID(ZigBeeBindingConstants.BINDING_ID, thingProperties.getKey()));
        }

        return thingTypes;
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
