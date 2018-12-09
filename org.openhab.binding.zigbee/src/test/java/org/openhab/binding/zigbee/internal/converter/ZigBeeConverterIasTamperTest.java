package org.openhab.binding.zigbee.internal.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.junit.Before;
import org.junit.Test;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;

/**
 * Unit tests for the {@link ZigBeeConverterIasTamper}.
 * 
 * @author Tommaso Travaglino - initial contribution
 */
public class ZigBeeConverterIasTamperTest {

    private ZigBeeConverterIasTamper converter;
    private ZigBeeThingHandler thingHandler;
    private Channel channel;
    private ZigBeeCoordinatorHandler coordinatorHandler;
    private ZigBeeEndpoint endpoint;
    private ThingUID thingUID;

    @Before
    public void setup() {
        IeeeAddress ieeeAddress = new IeeeAddress();
        int endpointId = 1;

        thingUID = new ThingUID("bindingId", "thingTypeId", "id");

        ZclCluster zclCluster = mock(ZclIasZoneCluster.class);

        endpoint = mock(ZigBeeEndpoint.class);
        when(endpoint.getEndpointId()).thenReturn(endpointId);
        when(endpoint.getIeeeAddress()).thenReturn(ieeeAddress);
        when(endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID)).thenReturn(zclCluster);

        channel = mock(Channel.class);
        thingHandler = mock(ZigBeeThingHandler.class);

        coordinatorHandler = mock(ZigBeeCoordinatorHandler.class);
        when(coordinatorHandler.getEndpoint(ieeeAddress, endpointId)).thenReturn(endpoint);

        converter = new ZigBeeConverterIasTamper();
        converter.initialize(thingHandler, channel, coordinatorHandler, ieeeAddress, endpointId);
        converter.initializeConverter();
    }

    @Test
    public void testBitTestIsProperlySet() {
        assertEquals(ZigBeeConverterIas.CIE_TAMPER, converter.bitTest);
    }

    @Test
    public void testEndpointHasIasZoneInputCluster() {
        Channel channel = converter.getChannel(thingUID, endpoint);
        assertNotNull(channel);

        assertTrue(channel.getUID().getId().endsWith(ZigBeeBindingConstants.CHANNEL_NAME_IAS_TAMPER));
        assertEquals(ZigBeeBindingConstants.CHANNEL_IAS_TAMPER, channel.getChannelTypeUID());
        assertEquals(ZigBeeBindingConstants.CHANNEL_LABEL_IAS_TAMPER, channel.getLabel());
    }

    @Test
    public void testEndpointHasNoIasZoneInputCluster() {
        // Change the endpoint mock to return null instead of the cluster
        when(endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID)).thenReturn(null);

        Channel channel = converter.getChannel(thingUID, endpoint);
        assertNull(channel);
    }


}
