/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffWithEffectCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * This channel supports changes through attribute updates, and also through received commands. This allows a switch
 * that is not connected to a load to send commands, or a switch that is connected to a load to send status (or both!).
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterSwitchOnoff extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener, ZclCommandListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchOnoff.class);

    private ZclOnOffCluster clusterOnOffClient;
    private ZclOnOffCluster clusterOnOffServer;

    @Override
    public boolean initializeConverter() {
        clusterOnOffClient = (ZclOnOffCluster) endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        clusterOnOffServer = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffClient == null && clusterOnOffServer == null) {
            logger.error("{}: Error opening device on/off controls", endpoint.getIeeeAddress());
            return false;
        }

        if (clusterOnOffServer != null) {
            try {
                CommandResult bindResponse = bind(clusterOnOffServer).get();
                if (bindResponse.isSuccess()) {
                    // Configure reporting
                    CommandResult reportingResponse = clusterOnOffServer
                            .setOnOffReporting(REPORTING_PERIOD_DEFAULT_MIN, REPORTING_PERIOD_DEFAULT_MAX).get();
                    if (reportingResponse.isError()) {
                        pollingPeriod = POLLING_PERIOD_HIGH;
                    }
                } else {
                    logger.error("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(),
                            Integer.toHexString(bindResponse.getStatusCode()));
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            }

            // Add the listener
            clusterOnOffServer.addAttributeListener(this);
        }

        if (clusterOnOffClient != null) {
            try {
                CommandResult bindResponse = bind(clusterOnOffClient).get();
                if (!bindResponse.isSuccess()) {
                    logger.error("{}: Error 0x{} setting client binding", endpoint.getIeeeAddress(),
                            Integer.toHexString(bindResponse.getStatusCode()));
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("{}: Exception setting binding ", endpoint.getIeeeAddress(), e);
            }

            // Add the command listener
            clusterOnOffClient.addCommandListener(this);
        }

        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device on/off cluster", endpoint.getIeeeAddress());

        if (clusterOnOffClient != null) {
            clusterOnOffClient.removeCommandListener(this);
        }
        if (clusterOnOffServer != null) {
            clusterOnOffServer.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        if (clusterOnOffServer != null) {
            clusterOnOffServer.getOnOff(0);
        }
    }

    @Override
    public void handleCommand(final Command command) {
        if (clusterOnOffServer == null) {
            logger.warn("{}: OnOff converter is not linked to a server and cannot accept commands",
                    endpoint.getIeeeAddress());
            return;
        }

        OnOffType cmdOnOff = null;
        if (command instanceof PercentType) {
            if (((PercentType) command).intValue() == 0) {
                cmdOnOff = OnOffType.OFF;
            } else {
                cmdOnOff = OnOffType.ON;
            }
        } else if (command instanceof OnOffType) {
            cmdOnOff = (OnOffType) command;
        } else {
            logger.warn("{}: OnOff converter only accepts PercentType and OnOffType - not {}",
                    endpoint.getIeeeAddress(), command.getClass().getSimpleName());
            return;
        }

        if (cmdOnOff == OnOffType.ON) {
            clusterOnOffServer.onCommand();
        } else {
            clusterOnOffServer.offCommand();
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID) == null
                && endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID) == null) {
            logger.trace("{}: OnOff cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_SWITCH_ONOFF),
                        ZigBeeBindingConstants.ITEM_TYPE_SWITCH)
                .withType(ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_SWITCH_ONOFF).withProperties(createProperties(endpoint))
                .build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.ON_OFF && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            Boolean value = (Boolean) attribute.getLastValue();
            if (value != null && value) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }

    @Override
    public void commandReceived(ZclCommand command) {
        logger.debug("{}: ZigBee command receiveds {}", endpoint.getIeeeAddress(), command);
        if (command instanceof OnCommand) {
            updateChannelState(OnOffType.ON);
        }
        if (command instanceof OffCommand || command instanceof OffWithEffectCommand) {
            updateChannelState(OnOffType.OFF);
        }
    }
}
