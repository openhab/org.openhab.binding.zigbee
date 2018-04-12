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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
 *
 */
public class ZigBeeThingTypeMatcher {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeThingTypeMatcher.class);

    private final Map<String, List<RequiredProperty>> discoveryProperties = new HashMap<>();

    private final static String DISCOVERY_PROPERTIES_FILE = "/discovery.txt";

    /**
     * Matches a set of properties to a single thing type. If no match is found, null is returned.
     * The matcher checks all registered thing types. If multiple matches are found, null is returned.
     *
     * @param properties a Map of strings containing the properties to match
     * @return the {@link ThingTypeUID} of the matched thing type, or null if no single thing type matched the filter
     *         properties
     */
    public ThingTypeUID matchThingType(Map<String, String> properties) {
        // Only create the index once
        synchronized (discoveryProperties) {
            if (discoveryProperties.isEmpty()) {
                try {
                    readDiscoveryProperties();
                } catch (IOException exception) {
                    logger.debug("IOException reading ZigBee discovery properties", exception);
                    return null;
                }
            }
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

    private void readDiscoveryProperties() throws IOException {
        InputStream input = ZigBeeThingTypeMatcher.class.getResourceAsStream(DISCOVERY_PROPERTIES_FILE);
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

    private class RequiredProperty {
        public String name;
        public String value;

        public RequiredProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
