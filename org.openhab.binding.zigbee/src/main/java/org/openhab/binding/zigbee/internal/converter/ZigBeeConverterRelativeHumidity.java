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
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.clusters.centralite.CentraliteManufacturerSpecificConstants;
import org.openhab.binding.zigbee.clusters.centralite.CentraliteRelativeHumidityMeasurementCluster;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclRelativeHumidityMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterTypeRegistry;
import com.zsmartsystems.zigbee.zcl.protocol.ZclStandardClusterType;

/**
 * Converter for the relative humidity channel. This converter can handle both the cluster for relative humidity from
 * the ZigBee cluster library, and the manufacturer-specific cluster provided by Centralite that serves the same
 * purpose.
 *
 * @author Chris Jackson - Initial Contribution
 * @author Henning Sudbrock - addition of manufacturer-specific cluster
 */
public class ZigBeeConverterRelativeHumidity extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterRelativeHumidity.class);

    private ZclRelativeHumidityMeasurementCluster relativeHumiditycluster;
    private CentraliteRelativeHumidityMeasurementCluster centraliteRelativeHumidityCluster;

    @Override
    public boolean initializeConverter() {
        relativeHumiditycluster = (ZclRelativeHumidityMeasurementCluster) endpoint
                .getInputCluster(ZclRelativeHumidityMeasurementCluster.CLUSTER_ID);

        if (relativeHumiditycluster == null) {
            centraliteRelativeHumidityCluster = (CentraliteRelativeHumidityMeasurementCluster) endpoint
                    .getInputCluster(CentraliteRelativeHumidityMeasurementCluster.CLUSTER_ID);
        }

        if (relativeHumiditycluster == null && centraliteRelativeHumidityCluster == null) {
            logger.error("{}: Error opening device relative humidity measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        if (relativeHumiditycluster != null) {
            bind(relativeHumiditycluster);
        } else {
            bind(centraliteRelativeHumidityCluster);
        }

        // Add a listener, then request the status
        if (relativeHumiditycluster != null) {
            relativeHumiditycluster.addAttributeListener(this);
        } else {
            centraliteRelativeHumidityCluster.addAttributeListener(this);
        }

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        if (relativeHumiditycluster != null) {
            relativeHumiditycluster.setMeasuredValueReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1);
        } else {
            centraliteRelativeHumidityCluster.setMeasuredValueReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1);
        }

        return true;
    }

    @Override
    public void disposeConverter() {
        if (relativeHumiditycluster != null) {
            relativeHumiditycluster.removeAttributeListener(this);
        } else {
            centraliteRelativeHumidityCluster.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        if (relativeHumiditycluster != null) {
            relativeHumiditycluster.getMeasuredValue(0);
        } else {
            centraliteRelativeHumidityCluster.getMeasuredValue(0);
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclRelativeHumidityMeasurementCluster.CLUSTER_ID) == null
                && endpoint.getInputCluster(CentraliteRelativeHumidityMeasurementCluster.CLUSTER_ID) == null) {
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
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if ((attribute.getCluster() == ZclStandardClusterType.RELATIVE_HUMIDITY_MEASUREMENT
                || attribute.getCluster() == ZclClusterTypeRegistry.getInstance().getByManufacturerAndClusterId(
                        CentraliteManufacturerSpecificConstants.MANUFACTURER_CODE,
                        CentraliteRelativeHumidityMeasurementCluster.CLUSTER_ID))
                && attribute.getId() == ZclRelativeHumidityMeasurementCluster.ATTR_MEASUREDVALUE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                updateChannelState(new DecimalType(BigDecimal.valueOf(value, 2)));
            }
        }
    }
}
