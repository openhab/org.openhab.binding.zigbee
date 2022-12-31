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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.config.ZclReportingConfig;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.AutoUpdatePolicy;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclWindowCoveringCluster;
import com.zsmartsystems.zigbee.zcl.clusters.windowcovering.WindowCoveringDownClose;
import com.zsmartsystems.zigbee.zcl.clusters.windowcovering.WindowCoveringGoToLiftPercentage;
import com.zsmartsystems.zigbee.zcl.clusters.windowcovering.WindowCoveringStop;
import com.zsmartsystems.zigbee.zcl.clusters.windowcovering.WindowCoveringUpOpen;
import com.zsmartsystems.zigbee.zcl.clusters.windowcovering.ZclWindowCoveringCommand;

/**
 * This channel supports the window covering cluster to raise and lower a window covering
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterWindowCoveringLift extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeConverterWindowCoveringLift.class);

    private ZclWindowCoveringCluster clusterServer;

    private ZclAttribute attributeServer;

    private ZclReportingConfig configReporting;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclWindowCoveringCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        ZclWindowCoveringCluster serverCluster = (ZclWindowCoveringCluster) endpoint
                .getInputCluster(ZclWindowCoveringCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.error("{}: Error opening device window covering controls", endpoint.getIeeeAddress());
            return false;
        }

        ZclReportingConfig reporting = new ZclReportingConfig(channel);

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster
                        .getAttribute(ZclWindowCoveringCluster.ATTR_CURRENTPOSITIONLIFTPERCENTAGE);
                CommandResult reportingResponse = attribute.setReporting(reporting.getReportingTimeMin(),
                        reporting.getReportingTimeMax(), reporting.getReportingChange()).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, reporting.getPollingPeriod());
            } else {
                logger.debug("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }

        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        clusterServer = (ZclWindowCoveringCluster) endpoint.getInputCluster(ZclWindowCoveringCluster.CLUSTER_ID);
        if (clusterServer == null) {
            logger.error("{}: Error opening device window covering controls", endpoint.getIeeeAddress());
            return false;
        }

        // Add the listener
        clusterServer.addAttributeListener(this);
        configReporting = new ZclReportingConfig(channel);

        configOptions = new ArrayList<>();
        configOptions.addAll(configReporting.getConfiguration());

        // Add the listener
        clusterServer.addAttributeListener(this);
        attributeServer = clusterServer.getAttribute(ZclWindowCoveringCluster.ATTR_CURRENTPOSITIONLIFTPERCENTAGE);

        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device window covering cluster", endpoint.getIeeeAddress());

        clusterServer.removeAttributeListener(this);
    }

    @Override
    public int getPollingPeriod() {
        if (configReporting != null) {
            return configReporting.getPollingPeriod();
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public void handleRefresh() {
        if (attributeServer != null) {
            attributeServer.readValue(0);
        }
    }

    @Override
    public void handleCommand(final Command command) {
        ZclWindowCoveringCommand zclCommand = null;
        // UpDown MoveStop Percent Refresh
        if (command instanceof UpDownType) {
            switch ((UpDownType) command) {
                case UP:
                    zclCommand = new WindowCoveringUpOpen();
                    break;
                case DOWN:
                    zclCommand = new WindowCoveringDownClose();
                    break;
                default:
                    break;
            }
        } else if (command instanceof StopMoveType) {
            switch ((StopMoveType) command) {
                case STOP:
                    zclCommand = new WindowCoveringStop();
                    break;
                default:
                    break;
            }
        } else if (command instanceof PercentType) {
            zclCommand = new WindowCoveringGoToLiftPercentage(((PercentType) command).intValue());
        }

        if (command == null) {
            logger.debug("{}: Command was not converted - {}", endpoint.getIeeeAddress(), command);
            return;
        }

        monitorCommandResponse(command, clusterServer.sendCommand(zclCommand));
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclWindowCoveringCluster serverCluster = (ZclWindowCoveringCluster) endpoint
                .getInputCluster(ZclWindowCoveringCluster.CLUSTER_ID);
        if (serverCluster == null) {
            logger.trace("{}: Window covering cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        try {
            if (serverCluster.discoverCommandsReceived(false).get()) {
                if (!(serverCluster.getSupportedCommandsReceived().contains(WindowCoveringDownClose.COMMAND_ID)
                        && serverCluster.getSupportedCommandsReceived().contains(WindowCoveringUpOpen.COMMAND_ID))) {
                    logger.trace("{}: Window covering cluster up/down commands not supported",
                            endpoint.getIeeeAddress());
                    return null;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering received commands in window covering cluster",
                    endpoint.getIeeeAddress(), e);
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_WINDOWCOVERING_LIFT),
                        ZigBeeBindingConstants.ITEM_TYPE_ROLLERSHUTTER)
                .withType(ZigBeeBindingConstants.CHANNEL_WINDOWCOVERING_LIFT)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_WINDOWCOVERING_LIFT)
                .withAutoUpdatePolicy(AutoUpdatePolicy.VETO).withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object value) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getId() == ZclWindowCoveringCluster.ATTR_CURRENTPOSITIONLIFTPERCENTAGE) {
            updateChannelState(new PercentType((Integer) value));
        }
    }
}
