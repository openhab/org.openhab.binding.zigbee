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
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOccupancySensingCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the occupancy sensor.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterOccupancy extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterOccupancy.class);

    private ZclOccupancySensingCluster clusterOccupancy;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclOccupancySensingCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        logger.debug("{}: Initialising device occupancy cluster", endpoint.getIeeeAddress());

        ZclOccupancySensingCluster serverClusterOccupancy = (ZclOccupancySensingCluster) endpoint
                .getInputCluster(ZclOccupancySensingCluster.CLUSTER_ID);
        if (serverClusterOccupancy == null) {
            logger.error("{}: Error opening occupancy cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterOccupancy).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult reportingResponse = serverClusterOccupancy
                        .setOccupancyReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
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
        clusterOccupancy = (ZclOccupancySensingCluster) endpoint.getInputCluster(ZclOccupancySensingCluster.CLUSTER_ID);
        if (clusterOccupancy == null) {
            logger.error("{}: Error opening occupancy cluster", endpoint.getIeeeAddress());
            return false;
        }

        // Add a listener, then request the status
        clusterOccupancy.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device occupancy cluster", endpoint.getIeeeAddress());

        clusterOccupancy.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        clusterOccupancy.getOccupancy(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclOccupancySensingCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Occupancy sensing cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_OCCUPANCY_SENSOR),
                        ZigBeeBindingConstants.ITEM_TYPE_SWITCH)
                .withType(ZigBeeBindingConstants.CHANNEL_OCCUPANCY_SENSOR)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_OCCUPANCY_SENSOR)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.OCCUPANCY_SENSING
                && attribute.getId() == ZclOccupancySensingCluster.ATTR_OCCUPANCY) {
            Integer value = (Integer) val;
            if (value != null && value == 1) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}
