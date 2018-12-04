/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.core.thing.DefaultSystemChannelTypeProvider;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterProvider;
import org.osgi.service.component.annotations.Component;

/**
 * The base {@link ZigBeeDefaultChannelConverterProvider} of the binding making the standard
 * {@link ZigBeeBaseChannelConverter}s available for the {@link ZigBeeChannelConverterFactory}.
 *
 * @author Thomas Höfer - Initial contribution
 */
@Component(immediate = true, service = ZigBeeChannelConverterProvider.class)
public final class ZigBeeDefaultChannelConverterProvider implements ZigBeeChannelConverterProvider {

    private final Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> channelMap = new HashMap<>();

    public ZigBeeDefaultChannelConverterProvider() {
        // Add all the converters into the map...
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_COLOR, ZigBeeConverterColorColor.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_COLOR_TEMPERATURE, ZigBeeConverterColorTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_DOORLOCK_STATE, ZigBeeConverterDoorLock.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ELECTRICAL_ACTIVEPOWER, ZigBeeConverterMeasurementPower.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_HUMIDITY_VALUE, ZigBeeConverterRelativeHumidity.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_CONTACTPORTAL1, ZigBeeConverterIasContactPortal1.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_CODETECTOR, ZigBeeConverterIasCoDetector.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_FIREINDICATION, ZigBeeConverterIasFireIndicator.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_MOTIONINTRUSION, ZigBeeConverterIasMotionIntrusion.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_MOTIONPRESENCE, ZigBeeConverterIasMotionPresence.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_STANDARDCIESYSTEM, ZigBeeConverterIasCieSystem.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_WATERSENSOR, ZigBeeConverterIasWaterSensor.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_LOWBATTERY, ZigBeeConverterIasLowBattery.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_TAMPER, ZigBeeConverterIasTamper.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ILLUMINANCE_VALUE, ZigBeeConverterIlluminance.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_OCCUPANCY_SENSOR, ZigBeeConverterOccupancy.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_POWER_BATTERYPERCENT, ZigBeeConverterBatteryPercent.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_POWER_BATTERYVOLTAGE, ZigBeeConverterBatteryVoltage.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_POWER_BATTERYALARM, ZigBeeConverterBatteryAlarm.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_PRESSURE_VALUE, ZigBeeConverterAtmosphericPressure.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF, ZigBeeConverterSwitchOnoff.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL, ZigBeeConverterSwitchLevel.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE, ZigBeeConverterTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ELECTRICAL_RMSVOLTAGE,
                ZigBeeConverterMeasurementRmsVoltage.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ELECTRICAL_RMSCURRENT,
                ZigBeeConverterMeasurementRmsCurrent.class);
        channelMap.put(DefaultSystemChannelTypeProvider.SYSTEM_BUTTON.getUID(), ZigBeeConverterGenericButton.class);
    }

    @Override
    public Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> getChannelConverters() {
        return channelMap;
    }
}
