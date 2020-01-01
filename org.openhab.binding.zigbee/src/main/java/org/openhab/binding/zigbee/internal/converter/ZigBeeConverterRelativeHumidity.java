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

import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclRelativeHumidityMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the relative humidity channel
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterRelativeHumidity extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterRelativeHumidity.class);

    private ZclRelativeHumidityMeasurementCluster cluster;

    @Override
    public boolean initializeDevice() {
        ZclRelativeHumidityMeasurementCluster serverCluster = (ZclRelativeHumidityMeasurementCluster) endpoint
                .getInputCluster(ZclRelativeHumidityMeasurementCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device relative humidity measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult response = serverCluster.setMeasuredValueReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1)
                        .get();
                handleReportingResponse(response, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            pollingPeriod = POLLING_PERIOD_HIGH;
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclRelativeHumidityMeasurementCluster) endpoint
                .getInputCluster(ZclRelativeHumidityMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device relative humidity measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        cluster.getMeasuredValue(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclRelativeHumidityMeasurementCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Relative humidity cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_HUMIDITY_VALUE),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_HUMIDITY_VALUE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_HUMIDITY_VALUE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.RELATIVE_HUMIDITY_MEASUREMENT
                && attribute.getId() == ZclRelativeHumidityMeasurementCluster.ATTR_MEASUREDVALUE) {
            Integer value = (Integer) val;
            updateChannelState(new DecimalType(BigDecimal.valueOf(value, 2)));
        }
    }
}
