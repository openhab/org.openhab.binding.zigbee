/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

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
import com.zsmartsystems.zigbee.zcl.clusters.ZclTemperatureMeasurementCluster;

/**
 * Converter for the temperature channel
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterTemperature extends ZigBeeChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterTemperature.class);

    private ZclTemperatureMeasurementCluster cluster;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }
        cluster = (ZclTemperatureMeasurementCluster) device.getCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("Error opening device temperature measurement cluster {}", device.getIeeeAddress());
            return;
        }

        cluster.bind();

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        cluster.getMeasuredValue(60);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        cluster.setMeasuredValueReporting(1, 600, 0.1);
        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        if (cluster != null) {
            cluster.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint device) {
        if (device.getCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID) == null) {
            return null;
        }
        return createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE,
                ZigBeeBindingConstants.ITEM_TYPE_NUMBER, "Temperature");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, device.getIeeeAddress());
        if (attribute.getId() == ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                updateChannelState(new DecimalType(BigDecimal.valueOf(value, 2)));
            }
        }
    }
}
