package org.openhab.binding.zigbee.discovery.internal;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.internal.ZigBeeThingTypeMatcher;

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
