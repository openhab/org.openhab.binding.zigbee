/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclFanControlCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * This channel supports fan control
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterFanControl extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterFanControl.class);

    private static final int MODE_OFF = 0;
    private static final int MODE_LOW = 1;
    private static final int MODE_MEDIUM = 2;
    private static final int MODE_HIGH = 3;
    private static final int MODE_ON = 4;
    private static final int MODE_AUTO = 5;

    private ZclFanControlCluster cluster;
    private ZclAttribute fanModeAttribute;

    @Override
    public boolean initializeDevice() {
        ZclFanControlCluster serverCluster = (ZclFanControlCluster) endpoint
                .getInputCluster(ZclFanControlCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device fan controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclFanControlCluster.ATTR_FANMODE);
                CommandResult reportingResponse = attribute.setReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclFanControlCluster) endpoint.getInputCluster(ZclFanControlCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device fan controls", endpoint.getIeeeAddress());
            return false;
        }

        fanModeAttribute = cluster.getAttribute(ZclFanControlCluster.ATTR_FANMODE);

        // Add the listener
        cluster.addAttributeListener(this);

        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device fan control cluster", endpoint.getIeeeAddress());

        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        fanModeAttribute.readValue(0);
    }

    @Override
    public void handleCommand(final Command command) {
        int value;
        if (command instanceof OnOffType) {
            value = command == OnOffType.ON ? MODE_ON : MODE_OFF;
        } else if (command instanceof DecimalType) {
            value = ((DecimalType) command).intValue();
        } else {
            logger.debug("{}: Unabled to convert fan mode {}", endpoint.getIeeeAddress(), command);
            return;
        }

        fanModeAttribute.writeValue(value);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclFanControlCluster cluster = (ZclFanControlCluster) endpoint.getInputCluster(ZclFanControlCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.trace("{}: Fan control cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        // TODO: Detect the supported features and provide these as a description
        ZclAttribute attribute = cluster.getAttribute(ZclFanControlCluster.ATTR_FANMODESEQUENCE);

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_FANCONTROL),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_FANCONTROL)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_FANCONTROL).withProperties(createProperties(endpoint))
                .build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.FAN_CONTROL
                && attribute.getId() == ZclFanControlCluster.ATTR_FANMODE) {
            Integer value = (Integer) val;
            if (value != null) {
                updateChannelState(new DecimalType(value));
            }
        }
    }
}
