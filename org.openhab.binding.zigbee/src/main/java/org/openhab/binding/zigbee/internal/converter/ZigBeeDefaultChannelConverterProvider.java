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

import java.util.HashMap;
import java.util.Map;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterProvider;
import org.openhab.binding.zigbee.internal.converter.warningdevice.ZigBeeConverterWarningDevice;
import org.openhab.core.thing.DefaultSystemChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeUID;
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
        channelMap.put(ZigBeeBindingConstants.CHANNEL_BINARYINPUT, ZigBeeConverterBinaryInput.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_CONTACTPORTAL1, ZigBeeConverterIasContactPortal1.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_CODETECTOR, ZigBeeConverterIasCoDetector.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_FIREINDICATION, ZigBeeConverterIasFireIndicator.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_MOTIONINTRUSION, ZigBeeConverterIasMotionIntrusion.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_MOTIONPRESENCE, ZigBeeConverterIasMotionPresence.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_STANDARDCIESYSTEM, ZigBeeConverterIasCieSystem.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_WATERSENSOR, ZigBeeConverterIasWaterSensor.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_MOVEMENTSENSOR, ZigBeeConverterIasMovement.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_IAS_VIBRATIONSENSOR, ZigBeeConverterIasVibration.class);
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
        channelMap.put(ZigBeeBindingConstants.CHANNEL_WARNING_DEVICE, ZigBeeConverterWarningDevice.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE, ZigBeeConverterTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ELECTRICAL_RMSVOLTAGE,
                ZigBeeConverterMeasurementRmsVoltage.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_ELECTRICAL_RMSCURRENT,
                ZigBeeConverterMeasurementRmsCurrent.class);
        channelMap.put(DefaultSystemChannelTypeProvider.SYSTEM_BUTTON.getUID(), ZigBeeConverterGenericButton.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_LOCALTEMPERATURE,
                ZigBeeConverterThermostatLocalTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_OUTDOORTEMPERATURE,
                ZigBeeConverterThermostatOutdoorTemperature.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_OCCUPIEDCOOLING,
                ZigBeeConverterThermostatOccupiedCooling.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_OCCUPIEDHEATING,
                ZigBeeConverterThermostatOccupiedHeating.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_UNOCCUPIEDCOOLING,
                ZigBeeConverterThermostatUnoccupiedCooling.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_UNOCCUPIEDHEATING,
                ZigBeeConverterThermostatUnoccupiedHeating.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_RUNNINGMODE,
                ZigBeeConverterThermostatRunningMode.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_HEATING_DEMAND,
                ZigBeeConverterThermostatPiHeatingDemand.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_COOLING_DEMAND,
                ZigBeeConverterThermostatPiCoolingDemand.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_THERMOSTAT_SYSTEMMODE, ZigBeeConverterThermostatSystemMode.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_FANCONTROL, ZigBeeConverterFanControl.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_WINDOWCOVERING_LIFT, ZigBeeConverterWindowCoveringLift.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_INSTANTANEOUS_DEMAND,
                ZigBeeConverterMeteringInstantaneousDemand.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SUMMATION_DELIVERED,
                ZigBeeConverterMeteringSummationDelivered.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_SUMMATION_RECEIVED,
                ZigBeeConverterMeteringSummationReceived.class);
        channelMap.put(ZigBeeBindingConstants.CHANNEL_TUYA_BUTTON, ZigBeeConverterTuyaButton.class);
    }

    @Override
    public Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> getChannelConverters() {
        return channelMap;
    }
}
