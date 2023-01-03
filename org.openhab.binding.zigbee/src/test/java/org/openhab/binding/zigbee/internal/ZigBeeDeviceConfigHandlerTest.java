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
package org.openhab.binding.zigbee.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.openhab.core.config.core.Configuration;

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

        Mockito.when(node.getEndpoint(ArgumentMatchers.anyInt())).thenReturn(endpoint);
        Mockito.when(endpoint.getInputCluster(ArgumentMatchers.anyInt())).thenReturn(clusterIn);
        Mockito.when(endpoint.getOutputCluster(ArgumentMatchers.anyInt())).thenReturn(clusterOut);

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
}
