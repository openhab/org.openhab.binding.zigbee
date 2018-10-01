/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;

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

    // public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.unmodifiableSet(
    // Sets.newHashSet(THING_TYPE_GENERIC_DEVICE, THING_TYPE_PHILIPS_SML001, THING_TYPE_SMARTTHINGS_MOTIONV4));

    // List of Channel UIDs
    public static final String CHANNEL_NAME_SWITCH_ONOFF = "switch";
    public static final String CHANNEL_LABEL_SWITCH_ONOFF = "Switch";
    public static final ChannelTypeUID CHANNEL_SWITCH_ONOFF = new ChannelTypeUID("zigbee:switch_onoff");

    public static final String CHANNEL_NAME_SWITCH_LEVEL = "dimmer";
    public static final String CHANNEL_LABEL_SWITCH_LEVEL = "Level Control";
    public static final ChannelTypeUID CHANNEL_SWITCH_LEVEL = new ChannelTypeUID("zigbee:switch_level");

    public static final String CHANNEL_NAME_COLOR_COLOR = "color";
    public static final String CHANNEL_LABEL_COLOR_COLOR = "Color";
    public static final ChannelTypeUID CHANNEL_COLOR_COLOR = new ChannelTypeUID("zigbee:color_color");

    public static final String CHANNEL_NAME_COLOR_TEMPERATURE = "colortemperature";
    public static final String CHANNEL_LABEL_COLOR_TEMPERATURE = "Color Temperature";
    public static final ChannelTypeUID CHANNEL_COLOR_TEMPERATURE = new ChannelTypeUID("zigbee:color_temperature");

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

    public static final String CHANNEL_NAME_ELECTRICAL_RMSCURRENT = "current";
    public static final String CHANNEL_LABEL_ELECTRICAL_RMSCURRENT = "Current";
    public static final ChannelTypeUID CHANNEL_ELECTRICAL_RMSCURRENT = new ChannelTypeUID(
            "zigbee:electrical_rmscurrent");

    public static final String CHANNEL_PROPERTY_ENDPOINT = "zigbee_endpoint";

    public static final String ITEM_TYPE_COLOR = "Color";
    public static final String ITEM_TYPE_CONTACT = "Contact";
    public static final String ITEM_TYPE_DIMMER = "Dimmer";
    public static final String ITEM_TYPE_NUMBER = "Number";
    public static final String ITEM_TYPE_NUMBER_PRESSURE = "Number:Pressure";
    public static final String ITEM_TYPE_NUMBER_TEMPERATURE = "Number:Temperature";
    public static final String ITEM_TYPE_SWITCH = "Switch";

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
    public final static String THING_PROPERTY_INSTALLCODE = "zigbee_installcode";

    // List of all configuration parameters
    public final static String CONFIGURATION_PANID = "zigbee_panid";
    public final static String CONFIGURATION_EXTENDEDPANID = "zigbee_extendedpanid";
    public final static String CONFIGURATION_CHANNEL = "zigbee_channel";
    public final static String CONFIGURATION_PORT = "zigbee_port";
    public final static String CONFIGURATION_BAUD = "zigbee_baud";
    public final static String CONFIGURATION_FLOWCONTROL = "zigbee_flowcontrol";
    public static final String CONFIGURATION_NETWORKKEY = "zigbee_networkkey";
    public static final String CONFIGURATION_LINKKEY = "zigbee_linkkey";
    public static final String CONFIGURATION_PASSWORD = "zigbee_password";
    public static final String CONFIGURATION_INITIALIZE = "zigbee_initialise";
    public static final String CONFIGURATION_TRUSTCENTREMODE = "zigbee_trustcentremode";
    public static final String CONFIGURATION_POWERMODE = "zigbee_powermode";
    public static final String CONFIGURATION_TXPOWER = "zigbee_txpower";

    public final static String CONFIGURATION_MACADDRESS = "zigbee_macaddress";
    public final static String CONFIGURATION_JOINENABLE = "zigbee_joinenable";
    public final static String CONFIGURATION_LEAVE = "zigbee_leave";

    public static final String OFFLINE_COMMS_FAIL = "@text/zigbee.status.offline_commserror";
    public static final String OFFLINE_BAD_RESPONSE = "@text/zigbee.status.offline_badresponse";
    public final static String OFFLINE_NOT_INITIALIZED = "@text/zigbee.status.offline_notinitialized";
    public final static String OFFLINE_INITIALIZE_FAIL = "@text/zigbee.status.offline_initializefail";
    public final static String OFFLINE_STARTUP_FAIL = "@text/zigbee.status.offline_startupfail";
    public final static String OFFLINE_NO_ADDRESS = "@text/zigbee.status.offline_noaddress";
    public final static String OFFLINE_NODE_NOT_FOUND = "@text/zigbee.status.offline_nodenotfound";
    public final static String OFFLINE_DISCOVERY_INCOMPLETE = "@text/zigbee.status.offline_discoveryincomplete";

    public final static String FIRMWARE_FAILED = "@text/zigbee.firmware.failed";

    // List of configuration values for flow control
    public final static Integer FLOWCONTROL_CONFIG_NONE = Integer.valueOf(0);
    public final static Integer FLOWCONTROL_CONFIG_HARDWARE_CTSRTS = Integer.valueOf(1);
    public final static Integer FLOWCONTROL_CONFIG_SOFTWARE_XONXOFF = Integer.valueOf(2);

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
     * @param date
     *            Date
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
