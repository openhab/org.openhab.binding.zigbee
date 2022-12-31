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
package org.openhab.binding.zigbee.discovery;

import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;

import com.zsmartsystems.zigbee.ZigBeeNode;

/**
 * A {@link ZigBeeDiscoveryParticipant} that is registered as a service is picked up by the ZigBeeDiscoveryService
 * and can contribute {@link DiscoveryResult}s from ZigBee scans.
 *
 * @author Chris Jackson
 *
 */
@NonNullByDefault
public interface ZigBeeDiscoveryParticipant {

    /**
     * Defines the list of thing types that this participant can identify
     *
     * @return a set of thing type UIDs for which discovery results can be created
     */
    public Set<ThingTypeUID> getSupportedThingTypeUIDs();

    /**
     * Creates a discovery result for a ZigBee device. Note that the {@link DiscoveryResult} must contain the properties
     * provided, but can contain additional properties as needed by the thing.
     *
     * @param bridgeUID the {@link ThingUID} of the bridge on which this node was discovered
     * @param node the {@link ZigBeeNode} found on the network
     * @param properties a map of properties that must be added to the {@link DiscoveryResult}
     * @return the discovery result or <code>null</code>, if the node is not supported by this participant
     */
    @Nullable
    public DiscoveryResult createResult(ThingUID bridgeUID, ZigBeeNode node, Map<String, Object> properties);

}
