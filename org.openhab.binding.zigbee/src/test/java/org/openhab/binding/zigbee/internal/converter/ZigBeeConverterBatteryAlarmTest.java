package org.openhab.binding.zigbee.internal.converter;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster.*;
import static com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;

import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
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

    @Before
    public void setup() {
        IeeeAddress ieeeAddress = new IeeeAddress();
        int endpointId = 1;

        endpoint = mock(ZigBeeEndpoint.class);
        channel = mock(Channel.class);
        thingHandler = mock(ZigBeeThingHandler.class);

        coordinatorHandler = mock(ZigBeeCoordinatorHandler.class);
        when(coordinatorHandler.getEndpoint(ieeeAddress, endpointId)).thenReturn(endpoint);

        converter = new ZigBeeConverterBatteryAlarm();
        converter.initialize(thingHandler, channel, coordinatorHandler, ieeeAddress, endpointId);
    }

    @Test
    public void testAttributeUpdateForMinThreshold() {
        // Bit 0 indicates BatteryMinThreshold
        converter.attributeUpdated(makeAlarmState(0b0001));
        Mockito.verify(thingHandler).setChannelState(channel.getUID(),
                new StringType(STATE_OPTION_BATTERY_MIN_THRESHOLD_REACHED));
    }

    @Test
    public void testAttributeUpdateForThreshold1() {
        // Bit 1 indicates threshold 1
        converter.attributeUpdated(makeAlarmState(0b0010));
        Mockito.verify(thingHandler).setChannelState(channel.getUID(),
                new StringType(STATE_OPTION_BATTERY_THRESHOLD_1_REACHED));
    }

    @Test
    public void testAttributeUpdateForThreshold2() {
        // Bit 2 indicates threshold 2
        converter.attributeUpdated(makeAlarmState(0b0100));
        Mockito.verify(thingHandler).setChannelState(channel.getUID(),
                new StringType(STATE_OPTION_BATTERY_THRESHOLD_2_REACHED));
    }

    @Test
    public void testAttributeUpdateForThreshold3() {
        // Bit 3 indicates threshold 3
        converter.attributeUpdated(makeAlarmState(0b1000));
        Mockito.verify(thingHandler).setChannelState(channel.getUID(),
                new StringType(STATE_OPTION_BATTERY_THRESHOLD_3_REACHED));
    }

    @Test
    public void testAttributeUpdateForNoThreshold() {
        converter.attributeUpdated(makeAlarmState(0b0000));
        Mockito.verify(thingHandler).setChannelState(channel.getUID(),
                new StringType(STATE_OPTION_BATTERY_NO_THRESHOLD_REACHED));
    }

    @Test
    public void testAttributeUpdateMultipleThresholds() {
        converter.attributeUpdated(makeAlarmState(0b1110));
        Mockito.verify(thingHandler).setChannelState(channel.getUID(),
                new StringType(STATE_OPTION_BATTERY_THRESHOLD_1_REACHED));
    }

    @Test
    public void testAttributeUpdateUnsuitableAttribute() {
        converter.attributeUpdated(new ZclAttribute(POWER_CONFIGURATION, ATTR_BATTERYALARMMASK, "alarm_mask",
                ZclDataType.BITMAP_32_BIT, false, true, false, true));
        Mockito.verify(thingHandler, never()).setChannelState(any(), any());
    }

    @Test
    public void testAttributeUpdateUnsuitableCluster() {
        converter.attributeUpdated(new ZclAttribute(FAN_CONTROL, ATTR_BATTERYALARMMASK, "bla",
                ZclDataType.BITMAP_32_BIT, false, true, false, true));
        Mockito.verify(thingHandler, never()).setChannelState(any(), any());
    }

    private ZclAttribute makeAlarmState(int bitmask) {
        ZclAttribute attribute = new ZclAttribute(POWER_CONFIGURATION, ATTR_BATTERYALARMSTATE, "alarm_state",
                ZclDataType.BITMAP_32_BIT, false, true, false, true);
        attribute.updateValue(Integer.valueOf(bitmask));
        return attribute;
    }

}
