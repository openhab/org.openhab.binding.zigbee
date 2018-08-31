/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.discovery;

import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;

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
