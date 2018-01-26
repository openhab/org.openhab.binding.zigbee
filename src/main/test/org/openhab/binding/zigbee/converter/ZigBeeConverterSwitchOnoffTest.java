package org.openhab.binding.zigbee.converter;

import static org.junit.Assert.assertEquals;

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.State;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Tests for OnOff converter
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeConverterSwitchOnoffTest {

    @Test
    public void testAttributeUpdated() {
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZigBeeCoordinatorHandler coordinatorHandler = Mockito.mock(ZigBeeCoordinatorHandler.class);
        Mockito.when(coordinatorHandler.getEndpoint(Matchers.any(), Matchers.anyInt())).thenReturn(endpoint);

        ZigBeeConverterSwitchOnoff converter = new ZigBeeConverterSwitchOnoff();
        ArgumentCaptor<ChannelUID> channelCapture = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCapture = ArgumentCaptor.forClass(State.class);
        ZigBeeThingHandler thingHandler = Mockito.mock(ZigBeeThingHandler.class);
        converter.initialize(thingHandler, new ChannelUID("a:b:c:d"), coordinatorHandler,
                new IeeeAddress("1234567890ABCDEF"), 1);
        ZclAttribute attribute = new ZclAttribute(ZclClusterType.ON_OFF, 0, "OnOff", ZclDataType.BOOLEAN, false, false,
                false, false);
        converter.attributeUpdated(attribute);
        Mockito.verify(thingHandler, Mockito.times(1)).setChannelState(channelCapture.capture(),
                stateCapture.capture());

        assertEquals(new ChannelUID("a:b:c:d"), channelCapture.getValue());
    }
}
