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
package org.openhab.binding.zigbee.ember.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.ember.internal.EmberConfiguration;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.thing.Bridge;

import com.zsmartsystems.zigbee.dongle.ember.EmberNcp;
import com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberMulticastTableEntry;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EzspConfigId;

/**
 *
 * @author Chris Jackson
 *
 */
public class EmberHandlerTest {
    class EmberHandlerForTest extends EmberHandler {
        public EmberHandlerForTest(Bridge coordinator, SerialPortManager serialPortManager,
                ZigBeeChannelConverterFactory channelFactory, ZigBeeDongleEzsp dongle) {
            super(coordinator, serialPortManager, channelFactory);
            this.zigbeeTransport = dongle;
        }

        @Override
        protected Configuration editConfiguration() {
            return new Configuration();
        }

        @Override
        protected <T> T getConfigAs(Class<T> clazz) {
            return (T) new EmberConfiguration();
        }
    }

    @Test
    public void setGroupRegistration() {
        Bridge bridge = Mockito.mock(Bridge.class);
        SerialPortManager serialPortManager = Mockito.mock(SerialPortManager.class);
        ZigBeeChannelConverterFactory zigBeeChannelConverterFactory = Mockito.mock(ZigBeeChannelConverterFactory.class);

        ZigBeeDongleEzsp dongle = Mockito.mock(ZigBeeDongleEzsp.class);
        EmberNcp ncp = Mockito.mock(EmberNcp.class);
        Mockito.when(dongle.getEmberNcp()).thenReturn(ncp);
        EmberHandler handler = new EmberHandlerForTest(bridge, serialPortManager, zigBeeChannelConverterFactory,
                dongle);

        ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<EmberMulticastTableEntry> valueCaptor = ArgumentCaptor.forClass(EmberMulticastTableEntry.class);

        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ZigBeeBindingConstants.CONFIGURATION_GROUPREGISTRATION, "1234,5678, 90AB ,CDEF");
        handler.handleConfigurationUpdate(cfg);

        Mockito.verify(dongle).updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_MULTICAST_TABLE_SIZE, 7);
        Mockito.verify(ncp, Mockito.times(4)).setMulticastTableEntry(indexCaptor.capture(), valueCaptor.capture());

        int[] mcastId = { 0x1234, 0x5678, 0x90AB, 0xCDEF };
        for (int cnt = 0; cnt < 4; cnt++) {
            assertEquals(cnt, indexCaptor.getAllValues().get(cnt));
            assertEquals(1, valueCaptor.getAllValues().get(cnt).getEndpoint());
            assertEquals(0, valueCaptor.getAllValues().get(cnt).getNetworkIndex());
            assertEquals(mcastId[cnt], valueCaptor.getAllValues().get(cnt).getMulticastId());
        }
    }

    @Test
    public void initializeDongleSpecific() {
        Bridge bridge = Mockito.mock(Bridge.class);
        SerialPortManager serialPortManager = Mockito.mock(SerialPortManager.class);
        ZigBeeChannelConverterFactory zigBeeChannelConverterFactory = Mockito.mock(ZigBeeChannelConverterFactory.class);

        ZigBeeDongleEzsp dongle = Mockito.mock(ZigBeeDongleEzsp.class);
        EmberNcp ncp = Mockito.mock(EmberNcp.class);
        Mockito.when(dongle.getEmberNcp()).thenReturn(ncp);
        EmberHandler handler = new EmberHandlerForTest(bridge, serialPortManager, zigBeeChannelConverterFactory,
                dongle);

        handler.initializeDongleSpecific();
    }
}
