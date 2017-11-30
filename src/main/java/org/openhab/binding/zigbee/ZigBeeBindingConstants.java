/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
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
import java.util.Set;
import java.util.TimeZone;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * The {@link ZigBeeBinding} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeBindingConstants {

    // Binding Name
    public static final String BINDING_ID = "zigbee";

    // Coordinator (Bridges)
    public final static ThingTypeUID COORDINATOR_TYPE_EMBER = new ThingTypeUID(BINDING_ID, "coordinator_ember");
    public final static ThingTypeUID COORDINATOR_TYPE_CC2531 = new ThingTypeUID(BINDING_ID, "coordinator_cc2531");
    public final static ThingTypeUID COORDINATOR_TYPE_TELEGESIS = new ThingTypeUID(BINDING_ID, "coordinator_telegesis");

    // List of Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_GENERIC_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Sets.newHashSet(THING_TYPE_GENERIC_DEVICE);

    // List of Channel UIDs
    public static final String CHANNEL_SWITCH_DIMMER = "switch_dimmer";
    public static final String CHANNEL_SWITCH_ONOFF = "switch_onoff";

    public static final String CHANNEL_COLOR_COLOR = "color_color";
    public static final String CHANNEL_COLOR_MODE = "color_mode";
    public static final String CHANNEL_COLOR_TEMPERATURE = "color_temperature";

    public static final String CHANNEL_TEMPERATURE_VALUE = "sensor_temperature";
    public static final String CHANNEL_HUMIDITY_VALUE = "sensor_humidity";

    public static final String CHANNEL_OCCUPANCY_SENSOR = "sensor_occupancy";

    public static final String CHANNEL_PROPERTY_ADDRESS = "zigbee_address";
    public static final String CHANNEL_PROPERTY_CLUSTER = "zigbee_cluster";

    public static final String CHANNEL_SWITCH_LEVEL = "switch_level";

    public static final String ITEM_TYPE_COLOR = "Color";
    public static final String ITEM_TYPE_NUMBER = "Number";
    public static final String ITEM_TYPE_DIMMER = "Dimmer";
    public static final String ITEM_TYPE_SWITCH = "Switch";

    public static final String THING_PROPERTY_MANUFACTURER = "zigbee_manufacturer";
    public static final String THING_PROPERTY_MODEL = "zigbee_model";
    public static final String THING_PROPERTY_HWVERSION = "zigbee_hwversion";
    public static final String THING_PROPERTY_STKVERSION = "zigbee_stkversion";
    public static final String THING_PROPERTY_ZCLVERSION = "zigbee_zclversion";
    public static final String THING_PROPERTY_APPVERSION = "zigbee_appversion";
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
    public static final String THING_PROPERTY_PERMITJOINING = "zigbee_permitjoining";
    public static final String THING_PROPERTY_ASSOCIATEDDEVICES = "zigbee_devices";

    // List of all configuration parameters
    public final static String CONFIGURATION_PANID = "zigbee_panid";
    public final static String CONFIGURATION_EXTENDEDPANID = "zigbee_extendedpanid";
    public final static String CONFIGURATION_CHANNEL = "zigbee_channel";
    public final static String CONFIGURATION_PORT = "zigbee_port";
    public final static String CONFIGURATION_BAUD = "zigbee_baud";
    public static final String CONFIGURATION_NETWORKKEY = "zigbee_networkkey";
    public static final String CONFIGURATION_INITIALIZE = "zigbee_initialise";

    public final static String CONFIGURATION_MACADDRESS = "zigbee_macaddress";
    public final static String CONFIGURATION_JOINENABLE = "zigbee_joinenable";
    public final static String CONFIGURATION_LEAVE = "zigbee_leave";

    public final static String CONFIGURATION_ZNP_MAGICNUMBER = "zigbee_znp_magicnumber";

    public final static Set<ThingTypeUID> SUPPORTED_BRIDGE_TYPES_UIDS = ImmutableSet.of(COORDINATOR_TYPE_CC2531);

    public final static String OFFLINE_NOT_INITIALIZED = "zigbee.status.offline_notinitialized";
    public final static String OFFLINE_INITIALIZE_FAIL = "zigbee.status.offline_initializefail";
    public final static String OFFLINE_STARTUP_FAIL = "zigbee.status.offline_startupfail";
    public final static String OFFLINE_NO_ADDRESS = "zigbee.status.offline_noaddress";
    public final static String OFFLINE_NODE_NOT_FOUND = "zigbee.status.offline_nodenotfound";

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
     * Convert a map into a json encoded string
     *
     * @param object
     * @return
     */
    public static String propertiesToJson(Map<String, Object> object) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        boolean first = true;
        for (String key : object.keySet()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            first = false;

            jsonBuilder.append("\"");
            jsonBuilder.append(key);
            jsonBuilder.append("\":\"");
            jsonBuilder.append(object.get(key));
            jsonBuilder.append("\"");
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }
}
