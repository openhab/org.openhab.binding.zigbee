/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster.ATTR_BATTERYALARMSTATE;
import static java.time.Duration.*;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;

import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclStandardClusterType;

/**
 * Converter for a battery alarm channel.
 * <p>
 * This converter relies on reports for the BatteryAlarmState attribute of the power configuration cluster, setting the
 * state of the battery alarm channel depending on the bits set in the BatteryAlarmState.
 * <p>
 * Possible future improvements:
 * <ul>
 * <li>The BatteryAlarmState provides battery level information for up to three batteries; this converter only considers
 * the information for the first battery.
 * <li>Devices might use alarms from the Alarms cluster instead of the BatteryAlarmState attribute to indicate battery
 * alarms. This is currently not supported by this converter.
 * <li>Devices might permit to configure the four battery level/voltage thresholds on which battery alarms are signaled;
 * such configuration is currently not supported.
 * </ul>
 *
 * @author Henning Sudbrock - Initial Contribution
 */
public class ZigBeeConverterBatteryAlarm extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final int ALARMSTATE_MIN_REPORTING_INTERVAL = (int) ofMinutes(10).getSeconds();
    private static final int ALARMSTATE_MAX_REPORTING_INTERVAL = (int) ofHours(2).getSeconds();

    private static final int MIN_THRESHOLD_BITMASK = 0b0001;
    private static final int THRESHOLD_1_BITMASK = 0b0010;
    private static final int THRESHOLD_2_BITMASK = 0b0100;
    private static final int THRESHOLD_3_BITMASK = 0b1000;

    private static final int BATTERY_ALARM_POLLING_PERIOD = (int) ofMinutes(30).getSeconds();

    private ZclPowerConfigurationCluster cluster;

    @Override
    public boolean initializeConverter() {
        logger.debug("{}: Initialising device battery alarm converter", endpoint.getIeeeAddress());

        cluster = (ZclPowerConfigurationCluster) endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening power configuration cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(cluster).get();
            if (bindResponse.isSuccess()) {
                CommandResult reportingResponse = cluster.setReporting(cluster.getAttribute(ATTR_BATTERYALARMSTATE),
                        ALARMSTATE_MIN_REPORTING_INTERVAL, ALARMSTATE_MAX_REPORTING_INTERVAL).get();
                if (reportingResponse.isError()) {
                    logger.debug("Could not configure reporting for the battery alarm state; polling every {} seconds",
                            BATTERY_ALARM_POLLING_PERIOD);
                    pollingPeriod = BATTERY_ALARM_POLLING_PERIOD;
                }
            } else {
                pollingPeriod = BATTERY_ALARM_POLLING_PERIOD;
                logger.debug(
                        "Could not bind to the power configuration cluster; polling battery alarm state every {} seconds",
                        BATTERY_ALARM_POLLING_PERIOD);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting of battery alarm state ", endpoint.getIeeeAddress(), e);
            return false;
        }

        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing battery alarm converter", endpoint.getIeeeAddress());
        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        cluster.getBatteryAlarmState(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclPowerConfigurationCluster powerConfigurationCluster = (ZclPowerConfigurationCluster) endpoint
                .getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (powerConfigurationCluster == null) {
            logger.trace("{}: Power configuration cluster not found on endpoint {}", endpoint.getIeeeAddress(),
                    endpoint.getEndpointId());
            return null;
        }

        try {
            if (!powerConfigurationCluster.discoverAttributes(false).get() && !powerConfigurationCluster
                    .isAttributeSupported(ZclPowerConfigurationCluster.ATTR_BATTERYALARMSTATE)) {
                logger.trace("{}: Power configuration cluster battery alarm state not supported",
                        endpoint.getIeeeAddress());
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in power configuration cluster",
                    endpoint.getIeeeAddress(), e);
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_POWER_BATTERYALARM),
                        ZigBeeBindingConstants.ITEM_TYPE_STRING)
                .withType(ZigBeeBindingConstants.CHANNEL_POWER_BATTERYALARM)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_POWER_BATTERYALARM)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        if (attribute.getCluster() == ZclStandardClusterType.POWER_CONFIGURATION
                && attribute.getId() == ZclPowerConfigurationCluster.ATTR_BATTERYALARMSTATE) {

            logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);

            // The value is a 32-bit bitmap, represented by an Integer
            Integer value = (Integer) attribute.getLastValue();
            if (value == null) {
                return;
            }

            if ((value & MIN_THRESHOLD_BITMASK) != 0) {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_MIN_THRESHOLD));
            } else if ((value & THRESHOLD_1_BITMASK) != 0) {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_THRESHOLD_1));
            } else if ((value & THRESHOLD_2_BITMASK) != 0) {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_THRESHOLD_2));
            } else if ((value & THRESHOLD_3_BITMASK) != 0) {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_THRESHOLD_3));
            } else {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_NO_THRESHOLD));
            }
        }
    }
}
