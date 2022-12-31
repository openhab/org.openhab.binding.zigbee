/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.measure.quantity.ElectricCurrent;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
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
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclElectricalMeasurementCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        logger.debug("{}: Initialising electrical measurement cluster", endpoint.getIeeeAddress());

        ZclElectricalMeasurementCluster serverClusterMeasurement = (ZclElectricalMeasurementCluster) endpoint
                .getInputCluster(ZclElectricalMeasurementCluster.CLUSTER_ID);
        if (serverClusterMeasurement == null) {
            logger.error("{}: Error opening electrical measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterMeasurement).get();
            if (bindResponse.isSuccess()) {
                ZclAttribute attribute = serverClusterMeasurement
                        .getAttribute(ZclElectricalMeasurementCluster.ATTR_RMSCURRENT);
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult reportingResponse = serverClusterMeasurement
                        .setReporting(attribute, 3, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
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
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        clusterMeasurement = (ZclElectricalMeasurementCluster) endpoint
                .getInputCluster(ZclElectricalMeasurementCluster.CLUSTER_ID);
        if (clusterMeasurement == null) {
            logger.error("{}: Error opening electrical measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        determineDivisorAndMultiplier(clusterMeasurement);

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
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.ELECTRICAL_MEASUREMENT
                && attribute.getId() == ZclElectricalMeasurementCluster.ATTR_RMSCURRENT) {
            Integer value = (Integer) val;
            BigDecimal valueInAmpere = BigDecimal.valueOf(value * multiplier / divisor);
            updateChannelState(new QuantityType<ElectricCurrent>(valueInAmpere, Units.AMPERE));
        }
    }

    private void determineDivisorAndMultiplier(ZclElectricalMeasurementCluster serverClusterMeasurement) {
        ZclAttribute divAttribute = serverClusterMeasurement
                .getAttribute(ZclElectricalMeasurementCluster.ATTR_ACPOWERDIVISOR);
        ZclAttribute mulAttribute = serverClusterMeasurement
                .getAttribute(ZclElectricalMeasurementCluster.ATTR_ACPOWERMULTIPLIER);

        divisor = (Integer) divAttribute.readValue(Long.MAX_VALUE);
        multiplier = (Integer) mulAttribute.readValue(Long.MAX_VALUE);
        if (divisor == null || multiplier == null) {
            divisor = 1;
            multiplier = 1;
        }
    }
}
