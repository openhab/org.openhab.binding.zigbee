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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.config.ZclOnOffSwitchConfig;
import org.openhab.binding.zigbee.internal.converter.config.ZclReportingConfig;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
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
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.ZclStatus;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffWithEffectCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnWithTimedOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ToggleCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ZclOnOffCommand;
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
    private final Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchOnoff.class);

    private ZclOnOffCluster clusterOnOffClient;
    private ZclOnOffCluster clusterOnOffServer;

    private ZclAttribute attributeServer;

    private ZclOnOffSwitchConfig configOnOff;
    private ZclReportingConfig configReporting;

    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);

    private ScheduledExecutorService updateScheduler;
    private ScheduledFuture<?> updateTimer = null;

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
        ZclOnOffCluster clientCluster = (ZclOnOffCluster) endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        ZclOnOffCluster serverCluster = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clientCluster == null && serverCluster == null) {
            logger.error("{}: Error opening device on/off controls", endpoint.getIeeeAddress());
            return false;
        }

        ZclReportingConfig reporting = new ZclReportingConfig(channel);

        if (serverCluster != null) {
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
        }

        if (clientCluster != null) {
            try {
                CommandResult bindResponse = bind(clientCluster).get();
                if (!bindResponse.isSuccess()) {
                    logger.error("{}: Error 0x{} setting client binding", endpoint.getIeeeAddress(),
                            Integer.toHexString(bindResponse.getStatusCode()));
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("{}: Exception setting binding ", endpoint.getIeeeAddress(), e);
            }
        }

        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        updateScheduler = Executors.newSingleThreadScheduledExecutor();

        clusterOnOffClient = (ZclOnOffCluster) endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        clusterOnOffServer = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffClient == null && clusterOnOffServer == null) {
            logger.error("{}: Error opening device on/off controls", endpoint.getIeeeAddress());
            return false;
        }

        if (clusterOnOffServer != null) {
            configOnOff = new ZclOnOffSwitchConfig();
            configOnOff.initialize(clusterOnOffServer);
            configReporting = new ZclReportingConfig(channel);

            configOptions = new ArrayList<>();
            configOptions.addAll(configOnOff.getConfiguration());
            configOptions.addAll(configReporting.getConfiguration());
        }

        if (clusterOnOffClient != null) {
            // Add the command listener
            clusterOnOffClient.addCommandListener(this);
        }

        if (clusterOnOffServer != null) {
            // Add the listener
            clusterOnOffServer.addAttributeListener(this);
            attributeServer = clusterOnOffServer.getAttribute(ZclOnOffCluster.ATTR_ONOFF);
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

        stopOffTimer();
        updateScheduler.shutdownNow();
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

        ZclOnOffCommand onOffCommand;
        if (cmdOnOff == OnOffType.ON) {
            onOffCommand = new OnCommand();
        } else {
            onOffCommand = new OffCommand();
        }
        monitorCommandResponse(command, clusterOnOffServer.sendCommand(onOffCommand));
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
                .withLabel(getDeviceTypeLabel(endpoint) + ": " + ZigBeeBindingConstants.CHANNEL_LABEL_SWITCH_ONOFF)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        if (clusterOnOffServer == null) {
            return;
        }
        if (configReporting.updateConfiguration(currentConfiguration, updatedParameters)) {
            try {
                ZclAttribute attribute;
                CommandResult reportingResponse;

                attribute = clusterOnOffServer.getAttribute(ZclOnOffCluster.ATTR_ONOFF);
                reportingResponse = attribute
                        .setReporting(configReporting.getReportingTimeMin(), configReporting.getReportingTimeMax())
                        .get();
                handleReportingResponse(reportingResponse, configReporting.getPollingPeriod(),
                        configReporting.getReportingTimeMax());
            } catch (InterruptedException | ExecutionException e) {
                logger.debug("{}: OnOff exception setting reporting", endpoint.getIeeeAddress(), e);
            }
        }

        configOnOff.updateConfiguration(currentConfiguration, updatedParameters);
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.ON_OFF && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            Boolean value = (Boolean) val;
            if (value != null && value) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        logger.debug("{}: ZigBee command received {}", endpoint.getIeeeAddress(), command);
        if (command instanceof OnCommand) {
            currentOnOffState.set(true);
            updateChannelState(OnOffType.ON);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof OnWithTimedOffCommand) {
            currentOnOffState.set(true);
            updateChannelState(OnOffType.ON);
            OnWithTimedOffCommand timedCommand = (OnWithTimedOffCommand) command;
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startOffTimer(timedCommand.getOnTime() * 100);
            return true;
        }
        if (command instanceof OffCommand || command instanceof OffWithEffectCommand) {
            currentOnOffState.set(false);
            updateChannelState(OnOffType.OFF);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof ToggleCommand) {
            currentOnOffState.set(!currentOnOffState.get());
            updateChannelState(currentOnOffState.get() ? OnOffType.ON : OnOffType.OFF);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }

        return false;
    }

    private void stopOffTimer() {
        if (updateTimer != null) {
            updateTimer.cancel(true);
            updateTimer = null;
        }
    }

    private void startOffTimer(int delay) {
        stopOffTimer();

        updateTimer = updateScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                logger.debug("{}: OnOff auto OFF timer expired", endpoint.getIeeeAddress());
                updateChannelState(OnOffType.OFF);
                updateTimer = null;
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
}
