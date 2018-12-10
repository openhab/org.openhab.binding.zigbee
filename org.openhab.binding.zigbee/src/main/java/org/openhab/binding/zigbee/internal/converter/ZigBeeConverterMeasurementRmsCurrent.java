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

import javax.measure.quantity.ElectricCurrent;

import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
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
import com.zsmartsystems.zigbee.zcl.clusters.ZclElectricalMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * ZigBee channel converter for RMS current measurement
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterMeasurementRmsCurrent extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterMeasurementRmsCurrent.class);

    private ZclElectricalMeasurementCluster clusterMeasurement;

    private Integer divisor;
    private Integer multiplier;

    @Override
    public boolean initializeConverter() {
        logger.debug("{}: Initialising electrical measurement cluster", endpoint.getIeeeAddress());

        clusterMeasurement = (ZclElectricalMeasurementCluster) endpoint
                .getInputCluster(ZclElectricalMeasurementCluster.CLUSTER_ID);
        if (clusterMeasurement == null) {
            logger.error("{}: Error opening electrical measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(clusterMeasurement).get();
            if (bindResponse.isSuccess()) {
                ZclAttribute attribute = clusterMeasurement
                        .getAttribute(ZclElectricalMeasurementCluster.ATTR_RMSCURRENT);
                // Configure reporting - no faster than once per second - no slower than 10 minutes.
                CommandResult reportingResponse = clusterMeasurement
                        .setReporting(attribute, 3, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                if (reportingResponse.isError()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }

        divisor = clusterMeasurement.getAcCurrentDivisor(Long.MAX_VALUE);
        multiplier = clusterMeasurement.getAcCurrentMultiplier(Long.MAX_VALUE);
        if (divisor == null || multiplier == null) {
            divisor = 1;
            multiplier = 1;
        }

        // Add a listener
        clusterMeasurement.addAttributeListener(this);

        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing electrical measurement cluster", endpoint.getIeeeAddress());

        clusterMeasurement.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        clusterMeasurement.getRmsCurrent(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclElectricalMeasurementCluster cluster = (ZclElectricalMeasurementCluster) endpoint
                .getInputCluster(ZclElectricalMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.trace("{}: Electrical measurement cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        try {
            if (!cluster.discoverAttributes(false).get()
                    && !cluster.isAttributeSupported(ZclElectricalMeasurementCluster.ATTR_RMSCURRENT)) {
                logger.trace("{}: Electrical measurement cluster RMS current not supported", endpoint.getIeeeAddress());

                return null;
            } else if (cluster.getRmsCurrent(Long.MAX_VALUE) == null) {
                logger.trace("{}: Electrical measurement cluster RMS current returned null", endpoint.getIeeeAddress());
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in electrical measurement cluster",
                    endpoint.getIeeeAddress(), e);
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_ELECTRICAL_RMSCURRENT),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_ELECTRICAL_RMSCURRENT)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_ELECTRICAL_RMSCURRENT)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.ELECTRICAL_MEASUREMENT
                && attribute.getId() == ZclElectricalMeasurementCluster.ATTR_RMSCURRENT) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                updateChannelState(new QuantityType<ElectricCurrent>(BigDecimal.valueOf(value * multiplier / divisor),
                        SmartHomeUnits.AMPERE));
            }
        }
    }
}
