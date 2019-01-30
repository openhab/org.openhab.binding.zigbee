/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.smarthome.config.core.Configuration;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

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
        ZigBeeNode node = mock(ZigBeeNode.class);
        ZigBeeEndpoint endpoint = mock(ZigBeeEndpoint.class);
        ZclCluster clusterIn = mock(ZclCluster.class);
        ZclCluster clusterOut = mock(ZclCluster.class);

        when(node.getEndpoint(anyInt())).thenReturn(endpoint);
        when(endpoint.getInputCluster(anyInt())).thenReturn(clusterIn);
        when(endpoint.getOutputCluster(anyInt())).thenReturn(clusterOut);

        ArgumentCaptor<Integer> attributeCapture = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ZclDataType> dataTypeCapture = ArgumentCaptor.forClass(ZclDataType.class);
        ArgumentCaptor<Object> valueCapture = ArgumentCaptor.forClass(Object.class);

        when(clusterIn.write(attributeCapture.capture(), dataTypeCapture.capture(), valueCapture.capture()))
                .thenReturn(null);
        when(clusterOut.write(attributeCapture.capture(), dataTypeCapture.capture(), valueCapture.capture()))
                .thenReturn(null);

        ZigBeeDeviceConfigHandler configHandler = new ZigBeeDeviceConfigHandler(node);

        Map<String, Object> configuration = new TreeMap<>();
        configuration.put("attribute_02_in_0404_0030_18", Integer.valueOf(1));
        configuration.put("attribute_02_out_0406_0032_29", Integer.valueOf(2));
        Configuration updatedConfig = new Configuration();
        configHandler.updateConfiguration(updatedConfig, configuration);

        assertEquals(2, updatedConfig.getProperties().size());

        assertEquals(2, attributeCapture.getAllValues().size());

        assertEquals(Integer.valueOf(0x30), attributeCapture.getAllValues().get(0));
        assertEquals(ZclDataType.getType(0x18), dataTypeCapture.getAllValues().get(0));
        assertEquals(Integer.valueOf(1), valueCapture.getAllValues().get(0));

        assertEquals(Integer.valueOf(0x32), attributeCapture.getAllValues().get(1));
        assertEquals(ZclDataType.getType(0x29), dataTypeCapture.getAllValues().get(1));
        assertEquals(Integer.valueOf(2), valueCapture.getAllValues().get(1));
    }

    @Test
    public void testIgnoreUnchangedParameters() {
        ZigBeeNode node = mock(ZigBeeNode.class);
        ZigBeeEndpoint endpoint = mock(ZigBeeEndpoint.class);
        ZclCluster clusterIn = mock(ZclCluster.class);
        ZclCluster clusterOut = mock(ZclCluster.class);

        when(node.getEndpoint(anyInt())).thenReturn(endpoint);
        when(endpoint.getInputCluster(anyInt())).thenReturn(clusterIn);
        when(endpoint.getOutputCluster(anyInt())).thenReturn(clusterOut);

        ArgumentCaptor<Integer> attributeCaptureIn = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ZclDataType> dataTypeCaptureIn = ArgumentCaptor.forClass(ZclDataType.class);
        ArgumentCaptor<Object> valueCaptureIn = ArgumentCaptor.forClass(Object.class);

        ArgumentCaptor<Integer> attributeCaptureOut = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ZclDataType> dataTypeCaptureOut = ArgumentCaptor.forClass(ZclDataType.class);
        ArgumentCaptor<Object> valueCaptureOut = ArgumentCaptor.forClass(Object.class);

        when(clusterIn.write(attributeCaptureIn.capture(), dataTypeCaptureIn.capture(), valueCaptureIn.capture()))
                .thenReturn(null);
        when(clusterOut.write(attributeCaptureOut.capture(), dataTypeCaptureOut.capture(), valueCaptureOut.capture()))
                .thenReturn(null);

        ZigBeeDeviceConfigHandler configHandler = new ZigBeeDeviceConfigHandler(node);

        // configuration which is stored
        Configuration currentConfiguration = new Configuration();
        currentConfiguration.put("attribute_02_in_0404_0030_18", new BigDecimal(1));
        currentConfiguration.put("attribute_02_out_0406_0032_29", new BigDecimal(1));

        // the new new configuration parameters
        Map<String, Object> updatedParameters = new TreeMap<>();
        updatedParameters.put("attribute_02_in_0404_0030_18", new BigDecimal(1)); // this value is unchanged
        updatedParameters.put("attribute_02_out_0406_0032_29", new BigDecimal(2)); // this value has changed

        configHandler.updateConfiguration(currentConfiguration, updatedParameters, true);

        assertEquals(2, currentConfiguration.getProperties().size());

        // we expect 0 capture for the in cluster as the parameter for the in cluster has not changed
        assertEquals(0, attributeCaptureIn.getAllValues().size());

        // we expect 1 capture for the out cluster as the parameter for the out cluster has changed
        assertEquals(1, attributeCaptureOut.getAllValues().size());
    }

    @Test
    public void testDoNotIgnoreUnchangedParameters() {
        ZigBeeNode node = mock(ZigBeeNode.class);
        ZigBeeEndpoint endpoint = mock(ZigBeeEndpoint.class);
        ZclCluster clusterIn = mock(ZclCluster.class);
        ZclCluster clusterOut = mock(ZclCluster.class);

        when(node.getEndpoint(anyInt())).thenReturn(endpoint);
        when(endpoint.getInputCluster(anyInt())).thenReturn(clusterIn);
        when(endpoint.getOutputCluster(anyInt())).thenReturn(clusterOut);

        ArgumentCaptor<Integer> attributeCaptureIn = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ZclDataType> dataTypeCaptureIn = ArgumentCaptor.forClass(ZclDataType.class);
        ArgumentCaptor<Object> valueCaptureIn = ArgumentCaptor.forClass(Object.class);

        ArgumentCaptor<Integer> attributeCaptureOut = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ZclDataType> dataTypeCaptureOut = ArgumentCaptor.forClass(ZclDataType.class);
        ArgumentCaptor<Object> valueCaptureOut = ArgumentCaptor.forClass(Object.class);

        when(clusterIn.write(attributeCaptureIn.capture(), dataTypeCaptureIn.capture(), valueCaptureIn.capture()))
                .thenReturn(null);
        when(clusterOut.write(attributeCaptureOut.capture(), dataTypeCaptureOut.capture(), valueCaptureOut.capture()))
                .thenReturn(null);

        ZigBeeDeviceConfigHandler configHandler = new ZigBeeDeviceConfigHandler(node);

        // configuration which is stored
        Configuration currentConfiguration = new Configuration();
        currentConfiguration.put("attribute_02_in_0404_0030_18", new BigDecimal(1));
        currentConfiguration.put("attribute_02_out_0406_0032_29", new BigDecimal(2));

        // the new new configuration parameters
        Map<String, Object> updatedParameters = new TreeMap<>();
        updatedParameters.put("attribute_02_in_0404_0030_18", new BigDecimal(1)); // this value is unchanged
        updatedParameters.put("attribute_02_out_0406_0032_29", new BigDecimal(2)); // this value has unchanged

        configHandler.updateConfiguration(currentConfiguration, updatedParameters, false);

        assertEquals(2, currentConfiguration.getProperties().size());

        // we expect 1 capture for the in cluster as the parameter is updated although it's unchanged
        assertEquals(1, attributeCaptureIn.getAllValues().size());

        // we expect 1 capture for the out cluster as the parameter is updated although it's unchanged
        assertEquals(1, attributeCaptureOut.getAllValues().size());
    }
}
