/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.eclipse.smarthome.core.i18n.I18nProvider;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.openhab.binding.zigbee.internal.ZigBeeActivator;

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
    public final static ThingTypeUID COORDINATOR_TYPE_CC2531 = new ThingTypeUID(BINDING_ID, "coordinator_cc2531");
    public final static ThingTypeUID COORDINATOR_TYPE_EMBER = new ThingTypeUID(BINDING_ID, "coordinator_ember");

    // List of Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_GENERIC_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Sets.newHashSet(THING_TYPE_GENERIC_DEVICE);

    // List of Channel ids
    public final static String CHANNEL_CFG_BINDING = "binding";

    public final static String CHANNEL_TEMPERATURE = "temperature";
    public final static String CHANNEL_SWITCH = "switch";
    public static final String CHANNEL_COLORTEMPERATURE = "color_temperature";
    public static final String CHANNEL_COLOR = "color";
    public static final String CHANNEL_BRIGHTNESS = "brightness";

    public static final String CHANNEL_SWITCH_DIMMER = "switch_dimmer";
    public static final String CHANNEL_SWITCH_ONOFF = "switch_onoff";

    public static final String CHANNEL_COLOR_COLOR = "color_color";
    public static final String CHANNEL_COLOR_TEMPERATURE = "color_temperature";

    public static final String CHANNEL_TEMPERATURE_VALUE = "sensor_temperature";
    public static final String CHANNEL_HUMIDITY_VALUE = "sensor_humidity";

    public static final String CHANNEL_PROPERTY_ADDRESS = "zigbee_address";
    public static final String CHANNEL_PROPERTY_CLUSTER = "zigbee_cluster";

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
    public static final String THING_PROPERTY_ASSOCIATEDDEVICES = "zigbee_devices";

    // List of all configuration parameters
    public final static String CONFIGURATION_PANID = "zigbee_panid";
    public final static String CONFIGURATION_EXTENDEDPANID = "zigbee_extendedpanid";
    public final static String CONFIGURATION_CHANNEL = "zigbee_channel";
    public final static String CONFIGURATION_PORT = "zigbee_port";
    public static final String CONFIGURATION_NETWORKKEY = "zigbee_networkkey";

    public final static String THING_PARAMETER_MACADDRESS = "zigbee_macaddress";

    public final static Set<ThingTypeUID> SUPPORTED_BRIDGE_TYPES_UIDS = ImmutableSet.of(COORDINATOR_TYPE_CC2531);

    public final static I18nConstant OFFLINE_NOT_INITIALIZED = new I18nConstant("zigbee.status.offline_notinitialized",
            "ZigBee transport layer not yet initialized");
    public final static I18nConstant OFFLINE_INITIALIZE_FAIL = new I18nConstant("zigbee.status.offline_initializefail",
            "Failed to initialize ZigBee transport layer");
    public final static I18nConstant OFFLINE_STARTUP_FAIL = new I18nConstant("zigbee.status.offline_startupfail",
            "Failed to startup ZigBee transport layer");
    public final static I18nConstant OFFLINE_NO_ADDRESS = new I18nConstant("zigbee.status.offline_noaddress",
            "Can't initializing ZigBee thing handler without address");
    public final static I18nConstant OFFLINE_NODE_NOT_FOUND = new I18nConstant("zigbee.status.offline_nodenotfound",
            "Node not found in ZigBee network");

    private static I18nProvider i18nProvider;

    protected void setI18nProvider(I18nProvider i18nProvider) {
        ZigBeeBindingConstants.i18nProvider = i18nProvider;
    }

    protected void unsetI18nProvider(I18nProvider i18nProvider) {
        ZigBeeBindingConstants.i18nProvider = null;
    }

    public static String getI18nConstant(I18nConstant constant) {
        I18nProvider i18nProviderLocal = i18nProvider;
        if (i18nProviderLocal == null) {
            return MessageFormat.format(constant.defaultText, (Object[]) null);
        }
        return i18nProviderLocal.getText(ZigBeeActivator.getContext().getBundle(), constant.key, constant.defaultText,
                null, (Object[]) null);
    }

    public static String getI18nConstant(I18nConstant constant, Object... arguments) {
        I18nProvider i18nProviderLocal = i18nProvider;
        if (i18nProviderLocal == null) {
            return MessageFormat.format(constant.defaultText, arguments);
        }
        return i18nProviderLocal.getText(ZigBeeActivator.getContext().getBundle(), constant.key, constant.defaultText,
                null, arguments);
    }

    public static class I18nConstant {
        public String key;
        public String defaultText;

        I18nConstant(String key, String defaultText) {
            this.key = key;
            this.defaultText = defaultText;
        }
    }

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
