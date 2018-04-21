package org.openhab.binding.zigbee.internal;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 *
 * @author Chris Jackson - Initial contribution
 *
 */
public class ZigBeeDeviceConfigHandlerTest {

    @Test
    public void test() {
        ZigBeeNode node = Mockito.mock(ZigBeeNode.class);
        ZigBeeEndpoint endpoint = Mockito.mock(ZigBeeEndpoint.class);
        ZclCluster clusterIn = Mockito.mock(ZclCluster.class);
        ZclCluster clusterOut = Mockito.mock(ZclCluster.class);

        Mockito.when(node.getEndpoint(Mockito.anyInt())).thenReturn(endpoint);
        Mockito.when(endpoint.getInputCluster(Mockito.anyInt())).thenReturn(clusterIn);
        Mockito.when(endpoint.getOutputCluster(Mockito.anyInt())).thenReturn(clusterOut);

        ArgumentCaptor<Integer> attributeCapture = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ZclDataType> dataTypeCapture = ArgumentCaptor.forClass(ZclDataType.class);
        ArgumentCaptor<Object> valueCapture = ArgumentCaptor.forClass(Object.class);

        Mockito.when(clusterIn.write(attributeCapture.capture(), dataTypeCapture.capture(), valueCapture.capture()))
                .thenReturn(null);
        Mockito.when(clusterOut.write(attributeCapture.capture(), dataTypeCapture.capture(), valueCapture.capture()))
                .thenReturn(null);

        ZigBeeDeviceConfigHandler configHandler = new ZigBeeDeviceConfigHandler(node);

        Map<String, Object> configuration = new TreeMap<>();
        configuration.put("attribute_02_in_0404_0030_18", Integer.valueOf(1));
        configuration.put("attribute_02_out_0406_0032_29", Integer.valueOf(2));
        Map<String, Object> updatedConfig = configHandler.handleConfigurationUpdate(configuration);

        assertEquals(2, updatedConfig.size());

        assertEquals(2, attributeCapture.getAllValues().size());

        assertEquals(Integer.valueOf(0x30), attributeCapture.getAllValues().get(0));
        assertEquals(ZclDataType.getType(0x18), dataTypeCapture.getAllValues().get(0));
        assertEquals(Integer.valueOf(1), valueCapture.getAllValues().get(0));

        assertEquals(Integer.valueOf(0x32), attributeCapture.getAllValues().get(1));
        assertEquals(ZclDataType.getType(0x29), dataTypeCapture.getAllValues().get(1));
        assertEquals(Integer.valueOf(2), valueCapture.getAllValues().get(1));
    }
}
