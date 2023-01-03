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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
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
    private ZclTemperatureMeasurementCluster clusterClient;
    private ZclAttribute attribute;
    private ZclAttribute attributeClient;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclTemperatureMeasurementCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.singleton(ZclTemperatureMeasurementCluster.CLUSTER_ID);
    }

    @Override
    public boolean initializeDevice() {
        ZclTemperatureMeasurementCluster clientCluster = (ZclTemperatureMeasurementCluster) endpoint
                .getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
        if (clientCluster == null) {
            // Nothing to do, but we still return success
            return true;
        }

        ZclTemperatureMeasurementCluster serverCluster = (ZclTemperatureMeasurementCluster) endpoint
                .getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device temperature measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster
                        .getAttribute(ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE);
                CommandResult reportingResponse = attribute.setReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 10).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
            } else {
                logger.debug("{}: Failed to bind temperature measurement cluster", endpoint.getIeeeAddress());
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
        cluster = (ZclTemperatureMeasurementCluster) endpoint
                .getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
        if (cluster != null) {
            attribute = cluster.getAttribute(ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE);
            // Add a listener
            cluster.addAttributeListener(this);
        } else {
            clusterClient = (ZclTemperatureMeasurementCluster) endpoint
                    .getOutputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
            attributeClient = clusterClient.getLocalAttribute(ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE);
            attributeClient.setImplemented(true);
        }

        if (cluster == null && clusterClient == null) {
            logger.error("{}: Error opening device temperature measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    @Override
    public void disposeConverter() {
        if (cluster != null) {
            cluster.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        if (attribute != null) {
            attribute.readValue(0);
        }
    }

    @Override
    public void handleCommand(final Command command) {
        if (attributeClient == null) {
            logger.warn("{}: Temperature measurement update but remote client not set", endpoint.getIeeeAddress(),
                    command, command.getClass().getSimpleName());
            return;
        }

        Integer value = temperatureToValue(command);

        if (value == null) {
            logger.warn("{}: Temperature measurement update {} [{}] was not processed", endpoint.getIeeeAddress(),
                    command, command.getClass().getSimpleName());
            return;
        }

        attributeClient.setValue(value);
        attributeClient.reportValue(value);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getOutputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID) == null
                && endpoint.getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Temperature measurement cluster not found", endpoint.getIeeeAddress());
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
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.TEMPERATURE_MEASUREMENT
                && attribute.getId() == ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE) {
            updateChannelState(valueToTemperature((Integer) val));
        }
    }
}
