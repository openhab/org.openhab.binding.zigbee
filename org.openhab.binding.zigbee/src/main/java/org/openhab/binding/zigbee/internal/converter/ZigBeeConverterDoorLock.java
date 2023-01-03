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
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclDoorLockCluster;
import com.zsmartsystems.zigbee.zcl.clusters.doorlock.LockDoorCommand;
import com.zsmartsystems.zigbee.zcl.clusters.doorlock.UnlockDoorCommand;
import com.zsmartsystems.zigbee.zcl.clusters.doorlock.ZclDoorLockCommand;
import com.zsmartsystems.zigbee.zcl.field.ByteArray;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * This channel supports changes through attribute updates to the door lock state. ON=Locked, OFF=Unlocked.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterDoorLock extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterDoorLock.class);

    private ZclDoorLockCluster cluster;
    private ZclAttribute attribute;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclDoorLockCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        ZclDoorLockCluster serverCluster = (ZclDoorLockCluster) endpoint.getInputCluster(ZclDoorLockCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device door lock controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                ZclAttribute attribute = serverCluster.getAttribute(ZclDoorLockCluster.ATTR_LOCKSTATE);
                CommandResult reportingResponse = attribute.setReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();

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
        cluster = (ZclDoorLockCluster) endpoint.getInputCluster(ZclDoorLockCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening device door lock controls", endpoint.getIeeeAddress());
            return false;
        }

        attribute = cluster.getAttribute(ZclDoorLockCluster.ATTR_LOCKSTATE);

        // Add the listener
        cluster.addAttributeListener(this);

        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device door lock cluster", endpoint.getIeeeAddress());

        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        attribute.readValue(0);
    }

    @Override
    public void handleCommand(final Command command) {
        ZclDoorLockCommand zclCommand;
        if (command == OnOffType.ON) {
            zclCommand = new LockDoorCommand(new ByteArray(new byte[0]));
        } else {
            zclCommand = new UnlockDoorCommand(new ByteArray(new byte[0]));
        }

        monitorCommandResponse(command, cluster.sendCommand(zclCommand));
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclDoorLockCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Door lock cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_DOORLOCK_STATE),
                        ZigBeeBindingConstants.ITEM_TYPE_SWITCH)
                .withType(ZigBeeBindingConstants.CHANNEL_DOORLOCK_STATE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_DOORLOCK_STATE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.DOOR_LOCK
                && attribute.getId() == ZclDoorLockCluster.ATTR_LOCKSTATE) {
            Integer value = (Integer) val;
            if (value != null && value == 1) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}
