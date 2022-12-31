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

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclMeteringCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * ZigBee channel converter for summation delivered measurement
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterMeteringSummationDelivered extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterMeteringSummationDelivered.class);

    private ZclMeteringCluster clusterMetering;

    private ZclAttribute attribute;

    private double divisor = 1.0;
    private double multiplier = 1.0;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclMeteringCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        logger.debug("{}: Initialising electrical measurement cluster", endpoint.getIeeeAddress());

        ZclMeteringCluster serverClusterMeasurement = (ZclMeteringCluster) endpoint
                .getInputCluster(ZclMeteringCluster.CLUSTER_ID);
        if (serverClusterMeasurement == null) {
            logger.error("{}: Error opening metering cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterMeasurement).get();
            if (bindResponse.isSuccess()) {
                ZclAttribute attribute = serverClusterMeasurement
                        .getAttribute(ZclMeteringCluster.ATTR_CURRENTSUMMATIONDELIVERED);
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult reportingResponse = attribute.setReporting(3, REPORTING_PERIOD_DEFAULT_MAX, 1L).get();
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
        clusterMetering = (ZclMeteringCluster) endpoint.getInputCluster(ZclMeteringCluster.CLUSTER_ID);
        if (clusterMetering == null) {
            logger.error("{}: Error opening metering cluster", endpoint.getIeeeAddress());
            return false;
        }

        attribute = clusterMetering.getAttribute(ZclMeteringCluster.ATTR_CURRENTSUMMATIONDELIVERED);

        determineDivisorAndMultiplier(clusterMetering);

        // Add a listener
        clusterMetering.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        clusterMetering.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        attribute.readValue(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclMeteringCluster cluster = (ZclMeteringCluster) endpoint.getInputCluster(ZclMeteringCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.trace("{}: Metering cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        try {
            if (!cluster.discoverAttributes(false).get()
                    && !cluster.isAttributeSupported(ZclMeteringCluster.ATTR_CURRENTSUMMATIONDELIVERED)) {
                logger.trace("{}: Metering cluster summation delivered not supported", endpoint.getIeeeAddress());

                return null;
            } else {
                ZclAttribute attribute = cluster.getAttribute(ZclMeteringCluster.ATTR_CURRENTSUMMATIONDELIVERED);
                if (attribute.readValue(Long.MAX_VALUE) == null) {

                    logger.trace("{}: Metering cluster summation delivered returned null", endpoint.getIeeeAddress());
                    return null;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in metering cluster", endpoint.getIeeeAddress(), e);
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_SUMMATION_DELIVERED),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_SUMMATION_DELIVERED)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_SUMMATION_DELIVERED)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.METERING
                && attribute.getId() == ZclMeteringCluster.ATTR_CURRENTSUMMATIONDELIVERED) {
            double value = ((Long) val).intValue();
            BigDecimal valueCalibrated = BigDecimal.valueOf(value * multiplier / divisor);
            updateChannelState(new DecimalType(valueCalibrated));
        }
    }

    private void determineDivisorAndMultiplier(ZclMeteringCluster clusterMetering) {
        ZclAttribute divisorAttribute = clusterMetering.getAttribute(ZclMeteringCluster.ATTR_DIVISOR);
        ZclAttribute multiplierAttribute = clusterMetering.getAttribute(ZclMeteringCluster.ATTR_MULTIPLIER);

        Integer iDiv = (Integer) divisorAttribute.readValue(Long.MAX_VALUE);
        Integer iMult = (Integer) multiplierAttribute.readValue(Long.MAX_VALUE);
        if (iDiv == null || iMult == null) {
            iDiv = 1;
            iMult = 1;
        }

        divisor = iDiv;
        multiplier = iMult;
    }

}
