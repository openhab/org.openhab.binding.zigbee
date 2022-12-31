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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.config.ZclReportingConfig;
import org.openhab.core.config.core.Configuration;
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
import com.zsmartsystems.zigbee.zcl.clusters.ZclIlluminanceMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the illuminance channel
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterIlluminance extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterIlluminance.class);

    private static BigDecimal CHANGE_DEFAULT = new BigDecimal(5000);
    private static BigDecimal CHANGE_MIN = new BigDecimal(10);
    private static BigDecimal CHANGE_MAX = new BigDecimal(20000);

    private ZclIlluminanceMeasurementCluster cluster;
    private ZclAttribute attribute;

    private ZclReportingConfig configReporting;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclIlluminanceMeasurementCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        ZclIlluminanceMeasurementCluster serverCluster = (ZclIlluminanceMeasurementCluster) endpoint
                .getInputCluster(ZclIlluminanceMeasurementCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device illuminance measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        ZclReportingConfig reporting = new ZclReportingConfig(channel);

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                ZclAttribute attribute = serverCluster
                        .getAttribute(ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE);
                CommandResult reportingResponse = attribute.setReporting(reporting.getReportingTimeMin(),
                        reporting.getReportingTimeMax(), reporting.getReportingChange()).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, reporting.getPollingPeriod());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.debug("{}: Exception configuring measured value reporting", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        cluster = (ZclIlluminanceMeasurementCluster) endpoint
                .getInputCluster(ZclIlluminanceMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device illuminance measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        attribute = cluster.getAttribute(ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE);
        if (attribute == null) {
            logger.error("{}: Error opening device illuminance measurement attribute", endpoint.getIeeeAddress());
            return false;
        }

        // Add a listener, then request the status
        cluster.addAttributeListener(this);

        // Create a configuration handler and get the available options
        configReporting = new ZclReportingConfig(channel);
        configReporting.setAnalogue(CHANGE_DEFAULT, CHANGE_MIN, CHANGE_MAX);
        configOptions = new ArrayList<>();
        configOptions.addAll(configReporting.getConfiguration());

        return true;
    }

    @Override
    public void disposeConverter() {
        cluster.removeAttributeListener(this);
    }

    @Override
    public int getPollingPeriod() {
        return configReporting.getPollingPeriod();
    }

    @Override
    public void handleRefresh() {
        attribute.readValue(0);
    }

    @Override
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        if (configReporting.updateConfiguration(currentConfiguration, updatedParameters)) {
            try {
                ZclAttribute attribute = cluster.getAttribute(ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE);
                CommandResult reportingResponse;
                reportingResponse = attribute.setReporting(configReporting.getReportingTimeMin(),
                        configReporting.getReportingTimeMax(), configReporting.getReportingChange()).get();
                handleReportingResponse(reportingResponse, configReporting.getPollingPeriod(),
                        configReporting.getReportingTimeMax());
            } catch (InterruptedException | ExecutionException e) {
                logger.debug("{}: Illuminance measurement exception setting reporting", endpoint.getIeeeAddress(), e);
            }
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclIlluminanceMeasurementCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Illuminance measurement cluster not found", endpoint.getIeeeAddress());
            return null;
        }
        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_ILLUMINANCE_VALUE),
                        ZigBeeBindingConstants.ITEM_TYPE_NUMBER)
                .withType(ZigBeeBindingConstants.CHANNEL_ILLUMINANCE_VALUE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_ILLUMINANCE_VALUE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.ILLUMINANCE_MEASUREMENT
                && attribute.getId() == ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE) {
            updateChannelState(new DecimalType(Math.pow(10.0, (Integer) val / 10000.0) - 1));
        }
    }
}
