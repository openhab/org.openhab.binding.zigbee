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
package org.openhab.binding.zigbee.internal.converter;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.types.State;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclFanControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Unit tests for the {@link ZigBeeConverterBatteryAlarm}.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class ZigBeeConverterBatteryAlarmTest {

    private ZigBeeConverterBatteryAlarm converter;

    private ZigBeeThingHandler thingHandler;
    private Channel channel;
    private ZigBeeCoordinatorHandler coordinatorHandler;
    private ZigBeeEndpoint endpoint;

    @BeforeEach
    public void setup() {
        IeeeAddress ieeeAddress = new IeeeAddress();
        int endpointId = 1;

        endpoint = mock(ZigBeeEndpoint.class);
        channel = mock(Channel.class);
        thingHandler = mock(ZigBeeThingHandler.class);

        coordinatorHandler = mock(ZigBeeCoordinatorHandler.class);
        when(coordinatorHandler.getEndpoint(ieeeAddress, endpointId)).thenReturn(endpoint);

        converter = new ZigBeeConverterBatteryAlarm();
        converter.initialize(channel, coordinatorHandler, ieeeAddress, endpointId);
        converter.initializeConverter(thingHandler);
    }

    @Test
    public void testAttributeUpdateForMinThreshold() {
        // Bit 0 indicates BatteryMinThreshold
        ZclAttribute attribute = makeAlarmState(0b0001);
        converter.attributeUpdated(attribute, attribute.getLastValue());
        verify(thingHandler).setChannelState(channel.getUID(), new StringType(STATE_OPTION_BATTERY_MIN_THRESHOLD));
    }

    @Test
    public void testAttributeUpdateForThreshold1() {
        // Bit 1 indicates threshold 1
        ZclAttribute attribute = makeAlarmState(0b0010);
        converter.attributeUpdated(attribute, attribute.getLastValue());
        verify(thingHandler).setChannelState(channel.getUID(), new StringType(STATE_OPTION_BATTERY_THRESHOLD_1));
    }

    @Test
    public void testAttributeUpdateForThreshold2() {
        // Bit 2 indicates threshold 2
        ZclAttribute attribute = makeAlarmState(0b0100);
        converter.attributeUpdated(attribute, attribute.getLastValue());
        verify(thingHandler).setChannelState(channel.getUID(), new StringType(STATE_OPTION_BATTERY_THRESHOLD_2));
    }

    @Test
    public void testAttributeUpdateForThreshold3() {
        // Bit 3 indicates threshold 3
        ZclAttribute attribute = makeAlarmState(0b1000);
        converter.attributeUpdated(attribute, attribute.getLastValue());
        verify(thingHandler).setChannelState(channel.getUID(), new StringType(STATE_OPTION_BATTERY_THRESHOLD_3));
    }

    @Test
    public void testAttributeUpdateForNoThreshold() {
        ZclAttribute attribute = makeAlarmState(0b0000);
        converter.attributeUpdated(attribute, attribute.getLastValue());
        verify(thingHandler).setChannelState(channel.getUID(), new StringType(STATE_OPTION_BATTERY_NO_THRESHOLD));
    }

    @Test
    public void testAttributeUpdateMultipleThresholds() {
        ZclAttribute attribute = makeAlarmState(0b1110);
        converter.attributeUpdated(attribute, attribute.getLastValue());
        verify(thingHandler).setChannelState(channel.getUID(), new StringType(STATE_OPTION_BATTERY_THRESHOLD_1));
    }

    @Test
    public void testAttributeUpdateUnsuitableAttribute() {
        ZclAttribute attribute = new ZclAttribute(new ZclPowerConfigurationCluster(endpoint), ATTR_BATTERYALARMMASK,
                "alarm_mask", ZclDataType.BITMAP_32_BIT, false, true, false, true);
        converter.attributeUpdated(attribute, attribute.getLastValue());
        verify(thingHandler, never()).setChannelState(any(ChannelUID.class), any(State.class));
    }

    @Test
    public void testAttributeUpdateUnsuitableCluster() {
        ZclAttribute attribute = new ZclAttribute(new ZclFanControlCluster(endpoint), ATTR_BATTERYALARMMASK, "bla",
                ZclDataType.BITMAP_32_BIT, false, true, false, true);
        converter.attributeUpdated(attribute, attribute.getLastValue());
        verify(thingHandler, never()).setChannelState(any(ChannelUID.class), any(State.class));
    }

    private ZclAttribute makeAlarmState(int bitmask) {
        ZclAttribute attribute = new ZclAttribute(new ZclPowerConfigurationCluster(endpoint), ATTR_BATTERYALARMSTATE,
                "alarm_state", ZclDataType.BITMAP_32_BIT, false, true, false, true);
        attribute.updateValue(Integer.valueOf(bitmask));
        return attribute;
    }

}
