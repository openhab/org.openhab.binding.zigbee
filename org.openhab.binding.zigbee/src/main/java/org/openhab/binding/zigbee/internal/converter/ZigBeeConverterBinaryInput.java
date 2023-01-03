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
import com.zsmartsystems.zigbee.zcl.clusters.ZclBinaryInputBasicCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the binary input sensor.
 *
 * @author Witold Sowa
 *
 */
public class ZigBeeConverterBinaryInput extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterBinaryInput.class);

    private ZclBinaryInputBasicCluster binaryInputCluster;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclBinaryInputBasicCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        logger.debug("{}: Initialising device binary input cluster", endpoint.getIeeeAddress());

        ZclBinaryInputBasicCluster binaryInputCluster = (ZclBinaryInputBasicCluster) endpoint
                .getInputCluster(ZclBinaryInputBasicCluster.CLUSTER_ID);
        if (binaryInputCluster == null) {
            logger.error("{}: Error opening binary input cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(binaryInputCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult reportingResponse = binaryInputCluster
                        .setPresentValueReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
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
        binaryInputCluster = (ZclBinaryInputBasicCluster) endpoint
                .getInputCluster(ZclBinaryInputBasicCluster.CLUSTER_ID);
        if (binaryInputCluster == null) {
            logger.error("{}: Error opening binary input cluster", endpoint.getIeeeAddress());
            return false;
        }

        binaryInputCluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device binary input cluster", endpoint.getIeeeAddress());

        binaryInputCluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        binaryInputCluster.getPresentValue(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclBinaryInputBasicCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Binary input sensing cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_BINARYINPUT),
                        ZigBeeBindingConstants.ITEM_TYPE_SWITCH)
                .withType(ZigBeeBindingConstants.CHANNEL_BINARYINPUT)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_BINARYINPUT).withProperties(createProperties(endpoint))
                .build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.BINARY_INPUT_BASIC
                && attribute.getId() == ZclBinaryInputBasicCluster.ATTR_PRESENTVALUE) {
            Boolean value = (Boolean) val;
            if (value == Boolean.TRUE) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}
