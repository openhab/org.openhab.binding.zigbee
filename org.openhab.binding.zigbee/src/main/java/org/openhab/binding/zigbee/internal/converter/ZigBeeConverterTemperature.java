/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclTemperatureMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the temperature channel
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterTemperature extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterTemperature.class);

    private ZclTemperatureMeasurementCluster cluster;

    @Override
    public boolean initializeConverter() {
        cluster = (ZclTemperatureMeasurementCluster) endpoint
                .getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device temperature measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        CommandResult bindResponse;
        try {
            bindResponse = cluster.bind().get();
            if (!bindResponse.isSuccess()) {
                logger.debug("{}: Failed to bind temperature measurement cluster", endpoint.getIeeeAddress());
            } else {
                // Configure reporting
                cluster.setMeasuredValueReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
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
        if (endpoint.getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID) == null) {
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_TEMPERATURE_VALUE),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER_TEMPERATURE)
                .withType(ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_TEMPERATURE_VALUE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.TEMPERATURE_MEASUREMENT
                && attribute.getId() == ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                updateChannelState(new QuantityType<>(BigDecimal.valueOf(value, 2), SIUnits.CELSIUS));
            }
        }
    }
}
