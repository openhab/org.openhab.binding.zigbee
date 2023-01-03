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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.internal.ZigBeeThingTypeMatcher;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeNode;

/**
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeDefaultDiscoveryParticipantTest {

    private ZigBeeDefaultDiscoveryParticipant getDiscoveryParticipant()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        ZigBeeDefaultDiscoveryParticipant participant = new ZigBeeDefaultDiscoveryParticipant();

        ZigBeeThingTypeMatcher matcherMock = Mockito.mock(ZigBeeThingTypeMatcher.class);
        Mockito.when(matcherMock.matchThingType((Map<String, Object>) ArgumentMatchers.any(Object.class)))
                .thenReturn(new ThingTypeUID("zigbee:thingtype"));

        Field matcher = participant.getClass().getDeclaredField("matcher");
        matcher.setAccessible(true);
        matcher.set(participant, matcherMock);

        return participant;
    }

    @Test
    public void createResult()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        ZigBeeNode node = Mockito.mock(ZigBeeNode.class);
        ZigBeeDefaultDiscoveryParticipant participant = getDiscoveryParticipant();

        Mockito.when(node.getIeeeAddress()).thenReturn(new IeeeAddress("1234567890ABCDEF"));

        Map<String, Object> properties = new HashMap<>();
        DiscoveryResult result = participant.createResult(new ThingUID("zigbee:bridgetype:bridgename"), node,
                properties);

        assertEquals(new ThingUID("zigbee:thingtype:bridgename:1234567890abcdef"), result.getThingUID());
    }
}
