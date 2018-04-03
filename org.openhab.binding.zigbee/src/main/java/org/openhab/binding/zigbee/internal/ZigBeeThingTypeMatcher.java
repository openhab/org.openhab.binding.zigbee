package org.openhab.binding.zigbee.internal;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

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

        ThingTypeUID matchedType = null;
        Collection<ThingType> thingTypes = thingTypeRegistry.getThingTypes(ZigBeeBindingConstants.BINDING_ID);
        for (ThingType thingType : thingTypes) {
            int totalProperties = 0;
            int matchedProperties = 0;
            for (Entry<String, String> property : thingType.getProperties().entrySet()) {
                String parts[] = property.getKey().split(":");
                if (parts.length != 2 || !THING_MATCHER_PROPERTY.equals(parts[0])) {
                    continue;
                }
                totalProperties++;
                if (!property.getValue().equals(properties.get(parts[1]))) {
                    break;
                }
                matchedProperties++;
            }

            if (matchedProperties == totalProperties && totalProperties != 0) {
                if (matchedType != null) {
                    // Don't allow duplicate matches
                    logger.debug("Duplicate match {} and {}", matchedType, thingType.getUID());
                    return null;
                }
                matchedType = thingType.getUID();
            }
        }

        return matchedType;
    }

}
