/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler.cluster;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeDevice;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclTemperatureMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeTemperatureClusterHandler extends ZigBeeClusterHandler implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeTemperatureClusterHandler.class);

    private ZclTemperatureMeasurementCluster cluster;

    private boolean initialised = false;

    @Override
    public int getClusterId() {
        return ZclClusterType.TEMPERATURE_MEASUREMENT.getId();
    }

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

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        cluster.getMeasuredValue(60);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        try {
            cluster.setMeasuredValueReporting(1, 600, 0.1).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        if (cluster != null) {
            // coordinator.closeCluster(clusterOnOff);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }

    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, ZigBeeDevice device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE, "Decimal",
                "Temperature"));

        return channels;
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
