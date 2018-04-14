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

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public boolean initializeConverter() {
        cluster = (ZclRelativeHumidityMeasurementCluster) endpoint
                .getInputCluster(ZclRelativeHumidityMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device relative humidity measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        cluster.bind();

        // Add a listener, then request the status
        cluster.addAttributeListener(this);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        cluster.setMeasuredValueReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1);
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
            return null;
        }
        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_HUMIDITY_VALUE,
                ZigBeeBindingConstants.ITEM_TYPE_NUMBER, "Relative Humidity");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.RELATIVE_HUMIDITY_MEASUREMENT
                && attribute.getId() == ZclRelativeHumidityMeasurementCluster.ATTR_MEASUREDVALUE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                updateChannelState(new DecimalType(BigDecimal.valueOf(value, 2)));
            }
        }
    }
}
