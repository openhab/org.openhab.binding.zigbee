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

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPressureMeasurementCluster.ATTR_SCALEDVALUE;
import static org.openhab.core.library.unit.MetricPrefix.HECTO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.measure.quantity.Pressure;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPressureMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the atmospheric pressure channel.
 * This channel will attempt to detect if the device is supporting the enhanced (scaled) value reports and use them if
 * they are available.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterAtmosphericPressure extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterAtmosphericPressure.class);

    private ZclPressureMeasurementCluster cluster;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclPressureMeasurementCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    /**
     * If enhancedScale is null, then the binding will use the MeasuredValue report,
     * otherwise it will use the ScaledValue report
     */
    private Integer enhancedScale = null;

    @Override
    public boolean initializeDevice() {
        ZclPressureMeasurementCluster serverCluster = (ZclPressureMeasurementCluster) endpoint
                .getInputCluster(ZclPressureMeasurementCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device pressure measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        // Check if the enhanced attributes are supported
        determineEnhancedScale(serverCluster);

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult reportingResponse;
                if (enhancedScale != null) {
                    reportingResponse = serverCluster.setReporting(serverCluster.getAttribute(ATTR_SCALEDVALUE), 1,
                            REPORTING_PERIOD_DEFAULT_MAX, 0.1).get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
                } else {
                    reportingResponse = serverCluster.setMeasuredValueReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1)
                            .get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
                }
            } else {
                logger.error("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
                pollingPeriod = POLLING_PERIOD_HIGH;
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            pollingPeriod = POLLING_PERIOD_HIGH;
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        cluster = (ZclPressureMeasurementCluster) endpoint.getInputCluster(ZclPressureMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device pressure measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        // Check if the enhanced attributes are supported
        determineEnhancedScale(cluster);

        // Add a listener
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        if (enhancedScale != null) {
            cluster.getScaledValue(0);
        } else {
            cluster.getMeasuredValue(0);
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclPressureMeasurementCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Pressure measurement cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_PRESSURE_VALUE),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER_PRESSURE)
                .withType(ZigBeeBindingConstants.CHANNEL_PRESSURE_VALUE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_PRESSURE_VALUE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public synchronized void attributeUpdated(ZclAttribute attribute, Object value) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() != ZclClusterType.PRESSURE_MEASUREMENT) {
            return;
        }

        // Handle automatic reporting of the enhanced attribute configuration
        if (attribute.getId() == ZclPressureMeasurementCluster.ATTR_SCALE) {
            enhancedScale = (Integer) value;
            if (enhancedScale != null) {
                enhancedScale *= -1;
            }
            return;
        }

        if (attribute.getId() == ZclPressureMeasurementCluster.ATTR_SCALEDVALUE && enhancedScale != null) {
            updateChannelState(new QuantityType<Pressure>(BigDecimal.valueOf((Integer) value, enhancedScale),
                    HECTO(SIUnits.PASCAL)));
            return;
        }

        if (attribute.getId() == ZclPressureMeasurementCluster.ATTR_MEASUREDVALUE && enhancedScale == null) {
            updateChannelState(
                    new QuantityType<Pressure>(BigDecimal.valueOf((Integer) value, 0), HECTO(SIUnits.PASCAL)));
        }
        return;
    }

    private void determineEnhancedScale(ZclPressureMeasurementCluster cluster) {
        if (cluster.getScaledValue(Long.MAX_VALUE) != null) {
            enhancedScale = cluster.getScale(Long.MAX_VALUE);
            if (enhancedScale != null) {
                enhancedScale *= -1;
            }
        }
    }

}
