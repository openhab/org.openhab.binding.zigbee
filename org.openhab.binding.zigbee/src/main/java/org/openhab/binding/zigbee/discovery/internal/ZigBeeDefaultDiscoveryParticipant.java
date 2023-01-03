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
package org.openhab.binding.zigbee.discovery.internal;

import java.util.Map;
import java.util.Set;

import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.binding.zigbee.discovery.ZigBeeDiscoveryParticipant;
import org.openhab.binding.zigbee.internal.ZigBeeThingTypeMatcher;
import org.osgi.service.component.annotations.Component;

import com.zsmartsystems.zigbee.ZigBeeNode;

/**
 * The default ZigBee discovery participant
 *
 * @author Chris Jackson
 *
 */
@Component(immediate = true)
public class ZigBeeDefaultDiscoveryParticipant implements ZigBeeDiscoveryParticipant {

    private final ZigBeeThingTypeMatcher matcher = new ZigBeeThingTypeMatcher();

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return matcher.getSupportedThingTypeUIDs();
    }

    @Override
    public DiscoveryResult createResult(ThingUID bridgeUID, ZigBeeNode node, Map<String, Object> properties) {
        ThingTypeUID thingTypeUID = matcher.matchThingType(properties);
        if (thingTypeUID == null) {
            return null;
        }

        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID,
                node.getIeeeAddress().toString().toLowerCase().replaceAll("[^a-z0-9_/]", ""));

        String label;
        // If we know the manufacturer and model, then give this device a name
        if ((properties.get(Thing.PROPERTY_VENDOR) != null) && (properties.get(Thing.PROPERTY_MODEL_ID) != null)) {
            label = properties.get(Thing.PROPERTY_VENDOR) + " " + properties.get(Thing.PROPERTY_MODEL_ID);
        } else {
            label = "Unknown ZigBee Device";
        }

        return DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID).withProperties(properties)
                .withBridge(bridgeUID).withLabel(label).build();
    }
}
