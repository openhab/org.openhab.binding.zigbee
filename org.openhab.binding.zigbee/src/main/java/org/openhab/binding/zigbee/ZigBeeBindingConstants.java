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
package org.openhab.binding.zigbee;

import static org.openhab.core.thing.DefaultSystemChannelTypeProvider.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * The {@link ZigBeeBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeBindingConstants {

    // Binding Name
    public static final String BINDING_ID = "zigbee";

    // List of Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_GENERIC_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    // List of Channel UIDs
    public static final String CHANNEL_NAME_SWITCH_ONOFF = "switch";
    public static final String CHANNEL_LABEL_SWITCH_ONOFF = "Switch";
    public static final ChannelTypeUID CHANNEL_SWITCH_ONOFF = new ChannelTypeUID("zigbee:switch_onoff");

    public static final String CHANNEL_NAME_SWITCH_LEVEL = "dimmer";
    public static final String CHANNEL_LABEL_SWITCH_LEVEL = "Level Control";
    public static final ChannelTypeUID CHANNEL_SWITCH_LEVEL = new ChannelTypeUID("zigbee:switch_level");

    public static final String CHANNEL_NAME_WARNING_DEVICE = "warning_device";
    public static final String CHANNEL_LABEL_WARNING_DEVICE = "Warning Device";
    public static final ChannelTypeUID CHANNEL_WARNING_DEVICE = new ChannelTypeUID("zigbee:warning_device");

    public static final String CHANNEL_NAME_COLOR_COLOR = "color";
    public static final String CHANNEL_LABEL_COLOR_COLOR = "Color";
    public static final ChannelTypeUID CHANNEL_COLOR_COLOR = new ChannelTypeUID("zigbee:color_color");

    public static final String CHANNEL_NAME_COLOR_TEMPERATURE = "colortemperature";
    public static final String CHANNEL_LABEL_COLOR_TEMPERATURE = "Color Temperature";
    public static final ChannelTypeUID CHANNEL_COLOR_TEMPERATURE = SYSTEM_COLOR_TEMPERATURE.getUID();

    public static final String CHANNEL_NAME_ILLUMINANCE_VALUE = "illuminance";
    public static final String CHANNEL_LABEL_ILLUMINANCE_VALUE = "Illuminance";
    public static final ChannelTypeUID CHANNEL_ILLUMINANCE_VALUE = new ChannelTypeUID("zigbee:measurement_illuminance");

    public static final String CHANNEL_NAME_TEMPERATURE_VALUE = "temperature";
    public static final String CHANNEL_LABEL_TEMPERATURE_VALUE = "Temperature";
    public static final ChannelTypeUID CHANNEL_TEMPERATURE_VALUE = new ChannelTypeUID("zigbee:measurement_temperature");

    public static final String CHANNEL_NAME_HUMIDITY_VALUE = "humidity";
    public static final String CHANNEL_LABEL_HUMIDITY_VALUE = "Humidity";
    public static final ChannelTypeUID CHANNEL_HUMIDITY_VALUE = new ChannelTypeUID(
            "zigbee:measurement_relativehumidity");

    public static final String CHANNEL_NAME_PRESSURE_VALUE = "pressure";
    public static final String CHANNEL_LABEL_PRESSURE_VALUE = "Atmospheric Pressure";
    public static final ChannelTypeUID CHANNEL_PRESSURE_VALUE = new ChannelTypeUID("zigbee:measurement_pressure");

    public static final String CHANNEL_NAME_OCCUPANCY_SENSOR = "occupancy";
    public static final String CHANNEL_LABEL_OCCUPANCY_SENSOR = "Occupancy";
    public static final ChannelTypeUID CHANNEL_OCCUPANCY_SENSOR = new ChannelTypeUID("zigbee:sensor_occupancy");

    public static final String CHANNEL_NAME_FANCONTROL = "fancontrol";
    public static final String CHANNEL_LABEL_FANCONTROL = "Fan Control";
    public static final ChannelTypeUID CHANNEL_FANCONTROL = new ChannelTypeUID("zigbee:fancontrol");

    public static final String CHANNEL_NAME_BINARYINPUT = "binaryinput";
    public static final String CHANNEL_LABEL_BINARYINPUT = "Binary Input";
    public static final ChannelTypeUID CHANNEL_BINARYINPUT = new ChannelTypeUID("zigbee:binaryinput");

    public static final String CHANNEL_NAME_IAS_CODETECTOR = "cosensor";
    public static final String CHANNEL_LABEL_IAS_CODETECTOR = "Carbon Monoxide Detector";
    public static final ChannelTypeUID CHANNEL_IAS_CODETECTOR = new ChannelTypeUID("zigbee:ias_cosensor");

    public static final String CHANNEL_NAME_IAS_CONTACTPORTAL1 = "contact1";
    public static final String CHANNEL_LABEL_IAS_CONTACTPORTAL1 = "Contact Portal 1";
    public static final ChannelTypeUID CHANNEL_IAS_CONTACTPORTAL1 = new ChannelTypeUID("zigbee:ias_contactportal1");

    public static final String CHANNEL_NAME_IAS_CONTACTPORTAL2 = "contact2";
    public static final String CHANNEL_LABEL_IAS_CONTACTPORTAL2 = "Contact Portal 2";
    public static final ChannelTypeUID CHANNEL_IAS_CONTACTPORTAL2 = new ChannelTypeUID("zigbee:ias_contactportal2");

    public static final String CHANNEL_NAME_IAS_MOTIONINTRUSION = "intrusion";
    public static final String CHANNEL_LABEL_IAS_MOTIONINTRUSION = "Motion Intrusion";
    public static final ChannelTypeUID CHANNEL_IAS_MOTIONINTRUSION = new ChannelTypeUID("zigbee:ias_motionintrusion");

    public static final String CHANNEL_NAME_IAS_MOTIONPRESENCE = "motion";
    public static final String CHANNEL_LABEL_IAS_MOTIONPRESENCE = "Motion Presence";
    public static final ChannelTypeUID CHANNEL_IAS_MOTIONPRESENCE = new ChannelTypeUID("zigbee:ias_motionpresence");

    public static final String CHANNEL_NAME_IAS_STANDARDCIESYSTEM = "system";
    public static final String CHANNEL_LABEL_IAS_STANDARDCIESYSTEM = "CIE System Alarm";
    public static final ChannelTypeUID CHANNEL_IAS_STANDARDCIESYSTEM = new ChannelTypeUID("zigbee:ias_standard_system");

    public static final String CHANNEL_NAME_IAS_FIREINDICATION = "fire";
    public static final String CHANNEL_LABEL_IAS_FIREINDICATION = "Fire Alarm";
    public static final ChannelTypeUID CHANNEL_IAS_FIREINDICATION = new ChannelTypeUID("zigbee:ias_fire");

    public static final String CHANNEL_NAME_IAS_WATERSENSOR = "water";
    public static final String CHANNEL_LABEL_IAS_WATERSENSOR = "Water Alarm";
    public static final ChannelTypeUID CHANNEL_IAS_WATERSENSOR = new ChannelTypeUID("zigbee:ias_water");

    public static final String CHANNEL_NAME_IAS_MOVEMENTSENSOR = "movement";
    public static final String CHANNEL_LABEL_IAS_MOVEMENTSENSOR = "Movement Alarm";
    public static final ChannelTypeUID CHANNEL_IAS_MOVEMENTSENSOR = new ChannelTypeUID("zigbee:ias_movement");

    public static final String CHANNEL_NAME_IAS_VIBRATIONSENSOR = "vibration";
    public static final String CHANNEL_LABEL_IAS_VIBRATIONSENSOR = "Vibration Alarm";
    public static final ChannelTypeUID CHANNEL_IAS_VIBRATIONSENSOR = new ChannelTypeUID("zigbee:ias_vibration");

    public static final String CHANNEL_NAME_IAS_LOWBATTERY = "iaslowbattery";
    public static final String CHANNEL_LABEL_IAS_LOWBATTERY = "Low Battery";
    public static final ChannelTypeUID CHANNEL_IAS_LOWBATTERY = SYSTEM_CHANNEL_LOW_BATTERY.getUID();

    public static final String CHANNEL_NAME_IAS_TAMPER = "tamper";
    public static final String CHANNEL_LABEL_IAS_TAMPER = "Tamper";
    public static final ChannelTypeUID CHANNEL_IAS_TAMPER = new ChannelTypeUID("zigbee:ias_tamper");

    public static final String CHANNEL_NAME_ELECTRICAL_ACTIVEPOWER = "activepower";
    public static final String CHANNEL_LABEL_ELECTRICAL_ACTIVEPOWER = "Total Active Power";
    public static final ChannelTypeUID CHANNEL_ELECTRICAL_ACTIVEPOWER = new ChannelTypeUID(
            "zigbee:electrical_activepower");

    public static final String CHANNEL_NAME_ELECTRICAL_RMSVOLTAGE = "voltage";
    public static final String CHANNEL_LABEL_ELECTRICAL_RMSVOLTAGE = "Voltage";
    public static final ChannelTypeUID CHANNEL_ELECTRICAL_RMSVOLTAGE = new ChannelTypeUID(
            "zigbee:electrical_rmsvoltage");

    public static final String CHANNEL_NAME_POWER_BATTERYPERCENT = "batterylevel";
    public static final String CHANNEL_LABEL_POWER_BATTERYPERCENT = "Battery Level";
    public static final ChannelTypeUID CHANNEL_POWER_BATTERYPERCENT = new ChannelTypeUID("system:battery-level");

    public static final String CHANNEL_NAME_POWER_BATTERYVOLTAGE = "batteryvoltage";
    public static final String CHANNEL_LABEL_POWER_BATTERYVOLTAGE = "Battery Voltage";
    public static final ChannelTypeUID CHANNEL_POWER_BATTERYVOLTAGE = new ChannelTypeUID("zigbee:battery_voltage");

    public static final String CHANNEL_NAME_POWER_BATTERYALARM = "batteryalarm";
    public static final String CHANNEL_LABEL_POWER_BATTERYALARM = "Battery Alarm";
    public static final ChannelTypeUID CHANNEL_POWER_BATTERYALARM = new ChannelTypeUID("zigbee:battery_alarm");

    public static final String CHANNEL_NAME_ELECTRICAL_RMSCURRENT = "current";
    public static final String CHANNEL_LABEL_ELECTRICAL_RMSCURRENT = "Current";
    public static final ChannelTypeUID CHANNEL_ELECTRICAL_RMSCURRENT = new ChannelTypeUID(
            "zigbee:electrical_rmscurrent");

    public static final String CHANNEL_NAME_THERMOSTAT_LOCALTEMPERATURE = "thermostatlocaltemp";
    public static final String CHANNEL_LABEL_THERMOSTAT_LOCALTEMPERATURE = "Local Temperature";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_LOCALTEMPERATURE = new ChannelTypeUID(
            "zigbee:thermostat_localtemp");

    public static final String CHANNEL_NAME_THERMOSTAT_OUTDOORTEMPERATURE = "thermostatoutdoortemp";
    public static final String CHANNEL_LABEL_THERMOSTAT_OUTDOORTEMPERATURE = "Outdoor Temperature";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_OUTDOORTEMPERATURE = new ChannelTypeUID(
            "zigbee:thermostat_outdoortemp");

    public static final String CHANNEL_NAME_THERMOSTAT_OCCUPIEDCOOLING = "thermostatoccupiedcooling";
    public static final String CHANNEL_LABEL_THERMOSTAT_OCCUPIEDCOOLING = "Occupied Cooling Setpoint";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_OCCUPIEDCOOLING = new ChannelTypeUID(
            "zigbee:thermostat_occupiedcooling");

    public static final String CHANNEL_NAME_THERMOSTAT_OCCUPIEDHEATING = "thermostatoccupiedheating";
    public static final String CHANNEL_LABEL_THERMOSTAT_OCCUPIEDHEATING = "Occupied Heating Setpoint";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_OCCUPIEDHEATING = new ChannelTypeUID(
            "zigbee:thermostat_occupiedheating");

    public static final String CHANNEL_NAME_THERMOSTAT_UNOCCUPIEDCOOLING = "thermostatunoccupiedcooling";
    public static final String CHANNEL_LABEL_THERMOSTAT_UNOCCUPIEDCOOLING = "Unoccupied Cooling Setpoint";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_UNOCCUPIEDCOOLING = new ChannelTypeUID(
            "zigbee:thermostat_unoccupiedcooling");

    public static final String CHANNEL_NAME_THERMOSTAT_UNOCCUPIEDHEATING = "thermostatunoccupiedheating";
    public static final String CHANNEL_LABEL_THERMOSTAT_UNOCCUPIEDHEATING = "Unoccupied Heating Setpoint";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_UNOCCUPIEDHEATING = new ChannelTypeUID(
            "zigbee:thermostat_unoccupiedheating");

    public static final String CHANNEL_NAME_THERMOSTAT_SYSTEMMODE = "thermostatsystemmode";
    public static final String CHANNEL_LABEL_THERMOSTAT_SYSTEMMODE = "System Mode";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_SYSTEMMODE = new ChannelTypeUID(
            "zigbee:thermostat_systemmode");

    public static final String CHANNEL_NAME_THERMOSTAT_RUNNINGMODE = "thermostatrunningmode";
    public static final String CHANNEL_LABEL_THERMOSTAT_RUNNINGMODE = "Running Mode";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_RUNNINGMODE = new ChannelTypeUID(
            "zigbee:thermostat_runningmode");

    public static final String CHANNEL_NAME_THERMOSTAT_HEATING_DEMAND = "thermostatheatingdemand";
    public static final String CHANNEL_LABEL_THERMOSTAT_HEATING_DEMAND = "Heating Demand";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_HEATING_DEMAND = new ChannelTypeUID(
            "zigbee:thermostat_heatingdemand");

    public static final String CHANNEL_NAME_THERMOSTAT_COOLING_DEMAND = "thermostatcoolingdemand";
    public static final String CHANNEL_LABEL_THERMOSTAT_COOLING_DEMAND = "Cooling Demand";
    public static final ChannelTypeUID CHANNEL_THERMOSTAT_COOLING_DEMAND = new ChannelTypeUID(
            "zigbee:thermostat_coolingdemand");

    public static final String CHANNEL_NAME_DOORLOCK_STATE = "doorlockstate";
    public static final String CHANNEL_LABEL_DOORLOCK_STATE = "Door Lock State";
    public static final ChannelTypeUID CHANNEL_DOORLOCK_STATE = new ChannelTypeUID("zigbee:door_state");

    public static final String CHANNEL_NAME_WINDOWCOVERING_LIFT = "windowcoveringlift";
    public static final String CHANNEL_LABEL_WINDOWCOVERING_LIFT = "Window Covering Lift";
    public static final ChannelTypeUID CHANNEL_WINDOWCOVERING_LIFT = new ChannelTypeUID("zigbee:windowcovering_lift");

    public static final String CHANNEL_NAME_INSTANTANEOUS_DEMAND = "meteringinstantdemand";
    public static final String CHANNEL_LABEL_INSTANTANEOUS_DEMAND = "Metering Instantaneous Demand";
    public static final ChannelTypeUID CHANNEL_INSTANTANEOUS_DEMAND = new ChannelTypeUID(
            "zigbee:metering_instantdemand");

    public static final String CHANNEL_NAME_SUMMATION_DELIVERED = "meteringsumdelivered";
    public static final String CHANNEL_LABEL_SUMMATION_DELIVERED = "Metering Summation Delivered";
    public static final ChannelTypeUID CHANNEL_SUMMATION_DELIVERED = new ChannelTypeUID("zigbee:metering_sumdelivered");

    public static final String CHANNEL_NAME_SUMMATION_RECEIVED = "meteringsumreceived";
    public static final String CHANNEL_LABEL_SUMMATION_RECEIVED = "Metering Summation Received";
    public static final ChannelTypeUID CHANNEL_SUMMATION_RECEIVED = new ChannelTypeUID("zigbee:metering_sumreceived");

    public static final String CHANNEL_NAME_TUYA_BUTTON = "tuyabutton";
    public static final String CHANNEL_LABEL_TUYA_BUTTON = "Button";
    public static final ChannelTypeUID CHANNEL_TUYA_BUTTON = new ChannelTypeUID("zigbee:tuya_button");

    public static final String CHANNEL_PROPERTY_ENDPOINT = "zigbee_endpoint";
    public static final String CHANNEL_PROPERTY_PROFILEID = "zigbee_profileid";
    public static final String CHANNEL_PROPERTY_INPUTCLUSTERS = "zigbee_inputclusters";
    public static final String CHANNEL_PROPERTY_OUTPUTCLUSTERS = "zigbee_outputclusters";

    public static final String ITEM_TYPE_COLOR = "Color";
    public static final String ITEM_TYPE_CONTACT = "Contact";
    public static final String ITEM_TYPE_DIMMER = "Dimmer";
    public static final String ITEM_TYPE_NUMBER = "Number";
    public static final String ITEM_TYPE_NUMBER_PRESSURE = "Number:Pressure";
    public static final String ITEM_TYPE_NUMBER_TEMPERATURE = "Number:Temperature";
    public static final String ITEM_TYPE_ROLLERSHUTTER = "Rollershutter";
    public static final String ITEM_TYPE_SWITCH = "Switch";
    public static final String ITEM_TYPE_STRING = "String";

    public static final String THING_PROPERTY_APPLICATIONVERSION = "zigbee_applicationVersion";
    public static final String THING_PROPERTY_STKVERSION = "zigbee_stkversion";
    public static final String THING_PROPERTY_ZCLVERSION = "zigbee_zclversion";
    public static final String THING_PROPERTY_DATECODE = "zigbee_datecode";
    public static final String THING_PROPERTY_LOGICALTYPE = "zigbee_logicaltype";
    public static final String THING_PROPERTY_NETWORKADDRESS = "zigbee_networkaddress";
    public static final String THING_PROPERTY_AVAILABLEPOWERSOURCES = "zigbee_powersources";
    public static final String THING_PROPERTY_POWERSOURCE = "zigbee_powersource";
    public static final String THING_PROPERTY_POWERMODE = "zigbee_powermode";
    public static final String THING_PROPERTY_POWERLEVEL = "zigbee_powerlevel";
    public static final String THING_PROPERTY_ROUTES = "zigbee_routes";
    public static final String THING_PROPERTY_NEIGHBORS = "zigbee_neighbors";
    public static final String THING_PROPERTY_LASTUPDATE = "zigbee_lastupdate";
    public static final String THING_PROPERTY_ASSOCIATEDDEVICES = "zigbee_devices";
    public static final String THING_PROPERTY_INSTALLCODE = "zigbee_installcode";
    public static final String THING_PROPERTY_STACKCOMPLIANCE = "zigbee_stkcompliance";
    public static final String THING_PROPERTY_DEVICE_INITIALIZED = "zigbee_device_initialised";
    public static final String THING_PROPERTY_MANUFACTURERCODE = "zigbee_manufacturercode";
    public static final String THING_PROPERTY_MACADDRESS = "zigbee_macaddress";

    // List of all configuration parameters
    public static final String CONFIGURATION_PANID = "zigbee_panid";
    public static final String CONFIGURATION_EXTENDEDPANID = "zigbee_extendedpanid";
    public static final String CONFIGURATION_CHANNEL = "zigbee_channel";
    public static final String CONFIGURATION_PORT = "zigbee_port";
    public static final String CONFIGURATION_BAUD = "zigbee_baud";
    public static final String CONFIGURATION_FLOWCONTROL = "zigbee_flowcontrol";
    public static final String CONFIGURATION_NETWORKKEY = "zigbee_networkkey";
    public static final String CONFIGURATION_LINKKEY = "zigbee_linkkey";
    public static final String CONFIGURATION_PASSWORD = "zigbee_password";
    public static final String CONFIGURATION_INITIALIZE = "zigbee_initialise";
    public static final String CONFIGURATION_INITIALIZE_DEVICE = "zigbee_initialise_device";
    public static final String CONFIGURATION_TRUSTCENTREMODE = "zigbee_trustcentremode";
    public static final String CONFIGURATION_POWERMODE = "zigbee_powermode";
    public static final String CONFIGURATION_TXPOWER = "zigbee_txpower";
    public static final String CONFIGURATION_MESHUPDATEPERIOD = "zigbee_meshupdateperiod";
    public static final String CONFIGURATION_GROUPREGISTRATION = "zigbee_groupregistration";

    public static final String CONFIGURATION_MACADDRESS = "zigbee_macaddress";
    public static final String CONFIGURATION_JOINENABLE = "zigbee_joinenable";
    public static final String CONFIGURATION_LEAVE = "zigbee_leave";

    public static final String OFFLINE_COMMS_FAIL = "@text/zigbee.status.offline_commserror";
    public static final String OFFLINE_BAD_RESPONSE = "@text/zigbee.status.offline_badresponse";
    public static final String OFFLINE_NOT_INITIALIZED = "@text/zigbee.status.offline_notinitialized";
    public static final String OFFLINE_INITIALIZE_FAIL = "@text/zigbee.status.offline_initializefail";
    public static final String OFFLINE_STARTUP_FAIL = "@text/zigbee.status.offline_startupfail";
    public static final String OFFLINE_NO_ADDRESS = "@text/zigbee.status.offline_noaddress";
    public static final String OFFLINE_NODE_NOT_FOUND = "@text/zigbee.status.offline_nodenotfound";
    public static final String OFFLINE_DISCOVERY_INCOMPLETE = "@text/zigbee.status.offline_discoveryincomplete";

    public static final String FIRMWARE_FAILED = "@text/zigbee.firmware.failed";
    public static final String FIRMWARE_VERSION_HEX_PREFIX = "0x";

    // List of channel state constants
    public static final String STATE_OPTION_BATTERY_MIN_THRESHOLD = "minThreshold";
    public static final String STATE_OPTION_BATTERY_THRESHOLD_1 = "threshold1";
    public static final String STATE_OPTION_BATTERY_THRESHOLD_2 = "threshold2";
    public static final String STATE_OPTION_BATTERY_THRESHOLD_3 = "threshold3";
    public static final String STATE_OPTION_BATTERY_NO_THRESHOLD = "noThreshold";

    // List of configuration values for flow control
    public static final Integer FLOWCONTROL_CONFIG_NONE = Integer.valueOf(0);
    public static final Integer FLOWCONTROL_CONFIG_HARDWARE_CTSRTS = Integer.valueOf(1);
    public static final Integer FLOWCONTROL_CONFIG_SOFTWARE_XONXOFF = Integer.valueOf(2);

    /**
     * Return an ISO 8601 combined date and time string for current date/time
     *
     * @return String with format "yyyy-MM-dd'T'HH:mm:ss'Z'"
     */
    public static String getISO8601StringForCurrentDate() {
        Date now = new Date();
        return getISO8601StringForDate(now);
    }

    /**
     * Return an ISO 8601 combined date and time string for specified date/time
     *
     * @param date Date
     * @return String with format "yyyy-MM-dd'T'HH:mm:ss'Z'"
     */
    public static String getISO8601StringForDate(Date date) {
        if (date == null) {
            return "";
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    /**
     * Convert a map into a json encoded string.
     *
     * @param properties a map with the to-be-converted properties.
     * @return a String with a JSON representation of the properties.
     */
    public static String propertiesToJson(Map<String, Object> properties) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            first = false;

            jsonBuilder.append("\"");
            jsonBuilder.append(entry.getKey());
            jsonBuilder.append("\":\"");
            jsonBuilder.append(entry.getValue());
            jsonBuilder.append("\"");
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }
}
