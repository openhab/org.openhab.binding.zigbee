/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.tuya.internal.converter;

import static java.lang.Integer.toHexString;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeBaseThingHandler;
import org.openhab.binding.zigbee.internal.converter.config.ZclReportingConfig;
import org.openhab.binding.zigbee.tuya.internal.zigbee.TuyaButtonPressCommand;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.ZclStatus;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;

/**
 * Generic converter for Tuya buttons (e.g., Zemismart).
 * <p>
 * This converter is handling the Tuya-specific command (ID: 253) and emits button-pressed events.
 * <p>
 * As the configuration is done via channel properties, this converter is usable via static thing types only.
 *
 * @author Daniel Schall - initial contribution
 * @author Chris Jackson - minor updates and refactoring to separate bundle
 */
public class ZigBeeConverterTuyaButton extends ZigBeeBaseChannelConverter implements ZclCommandListener {

    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterTuyaButton.class);

    private ZclCluster clientCluster = null;

    // Tuya devices sometimes send duplicate commands with the same tx id.
    // We keep track of the last received Tx id and ignore the duplicate.
    private Integer lastTxId = -1;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclOnOffCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.singleton(ZclOnOffCluster.CLUSTER_ID);
    }

    @Override
    public boolean initializeDevice() {
        ZclCluster clientCluster = endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        ZclCluster serverCluster = endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);

        if (clientCluster == null) {
            logger.error("{}: Error opening client cluster {} on endpoint {}", endpoint.getIeeeAddress(),
                    ZclOnOffCluster.CLUSTER_ID, endpoint.getEndpointId());
            return false;
        }

        // TODO Server side is not used in operation, so is it needed here?
        if (serverCluster == null) {
            logger.error("{}: Error opening server cluster {} on endpoint {}", endpoint.getIeeeAddress(),
                    ZclOnOffCluster.CLUSTER_ID, endpoint.getEndpointId());
            return false;
        }

        ZclReportingConfig reporting = new ZclReportingConfig(channel);

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclOnOffCluster.ATTR_ONOFF);
                CommandResult reportingResponse = attribute
                        .setReporting(reporting.getReportingTimeMin(), reporting.getReportingTimeMax()).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, reporting.getPollingPeriod());
            } else {
                logger.debug("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
                pollingPeriod = POLLING_PERIOD_HIGH;
            }

        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }

        try {
            CommandResult bindResponse = bind(clientCluster).get();
            if (!bindResponse.isSuccess()) {
                logger.error("{}: Error 0x{} setting client binding for cluster {}", endpoint.getIeeeAddress(),
                        toHexString(bindResponse.getStatusCode()), ZclOnOffCluster.CLUSTER_ID);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting client binding to cluster {}: {}", endpoint.getIeeeAddress(),
                    ZclOnOffCluster.CLUSTER_ID, e);
        }

        return true;
    }

    @Override
    public synchronized boolean initializeConverter(ZigBeeBaseThingHandler thing) {
        super.initializeConverter(thing);

        clientCluster = endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);

        if (clientCluster == null) {
            logger.error("{}: Error opening device client controls", endpoint.getIeeeAddress());
            return false;
        }

        clientCluster.addCommandListener(this);

        // Add Tuya-specific command
        HashMap<Integer, Class<? extends ZclCommand>> commandMap = new HashMap<>();
        commandMap.put(TuyaButtonPressCommand.COMMAND_ID, TuyaButtonPressCommand.class);
        clientCluster.addClientCommands(commandMap);

        return true;
    }

    @Override
    public void disposeConverter() {
        if (clientCluster != null) {
            clientCluster.removeCommandListener(this);
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        // This converter is used only for channels specified in static thing types, and cannot be used to construct
        // channels based on an endpoint alone.
        return null;
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        logger.debug("{}: Received command {}", endpoint.getIeeeAddress(), command);
        Integer thisTxId = command.getTransactionId();
        if (lastTxId == thisTxId) {
            logger.debug("{}: Ignoring duplicate command {}", endpoint.getIeeeAddress(), thisTxId);
        } else if (command instanceof TuyaButtonPressCommand) {
            TuyaButtonPressCommand tuyaButtonPressCommand = (TuyaButtonPressCommand) command;
            thing.triggerChannel(channel.getUID(), getEventType(tuyaButtonPressCommand.getPressType()));
            clientCluster.sendDefaultResponse(command, ZclStatus.SUCCESS);
        } else {
            logger.warn("{}: Received unknown command {}", endpoint.getIeeeAddress(), command);
        }

        lastTxId = thisTxId;
        return true;
    }

    private String getEventType(Integer pressType) {
        switch (pressType) {
            case 0:
                return CommonTriggerEvents.SHORT_PRESSED;
            case 1:
                return CommonTriggerEvents.DOUBLE_PRESSED;
            case 2:
                return CommonTriggerEvents.LONG_PRESSED;
            default:
                logger.warn("{} received unknown pressType {}", endpoint.getIeeeAddress(), pressType);
                return CommonTriggerEvents.SHORT_PRESSED;
        }
    }
}
