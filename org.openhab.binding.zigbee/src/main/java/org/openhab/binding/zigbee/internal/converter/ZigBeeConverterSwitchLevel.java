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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.config.ZclLevelControlConfig;
import org.openhab.binding.zigbee.internal.converter.config.ZclOnOffSwitchConfig;
import org.openhab.binding.zigbee.internal.converter.config.ZclReportingConfig;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.ZclStatus;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.StepCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.StepWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.StopCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.StopWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.ZclLevelControlCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffWithEffectCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnWithTimedOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ToggleCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ZclOnOffCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Level control converter uses both the {@link ZclLevelControlCluster} and the {@link ZclOnOffCluster}.
 * <p>
 * For the server side, if the {@link ZclOnOffCluster} has reported the device is OFF, then reports from
 * {@link ZclLevelControlCluster} are ignored. This is required as devices can report via the
 * {@link ZclLevelControlCluster} that they have a specified level, but still be OFF.
 *
 * @author Chris Jackson - Initial Contribution
 */
public class ZigBeeConverterSwitchLevel extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener, ZclCommandListener {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchLevel.class);

    // The number of milliseconds between state updates into OH when handling level control changes at a rate
    private static final int STATE_UPDATE_RATE = 50;

    // The number of milliseconds after the last IncreaseDecreaseType is received before sending the Stop command
    private static final int INCREASEDECREASE_TIMEOUT = 200;

    private ZclOnOffCluster clusterOnOffClient;
    private ZclLevelControlCluster clusterLevelControlClient;

    private ZclOnOffCluster clusterOnOffServer;
    private ZclLevelControlCluster clusterLevelControlServer;

    private ZclAttribute attributeOnOff;
    private ZclAttribute attributeLevel;

    private ZclReportingConfig configReporting;
    private ZclLevelControlConfig configLevelControl;
    private ZclOnOffSwitchConfig configOnOff;

    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);

    private PercentType lastLevel = PercentType.HUNDRED;

    private Command lastCommand;

    private ScheduledExecutorService updateScheduler;
    private ScheduledFuture<?> updateTimer = null;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Stream.of(ZclOnOffCluster.CLUSTER_ID, ZclLevelControlCluster.CLUSTER_ID).collect(Collectors.toSet());
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Stream.of(ZclOnOffCluster.CLUSTER_ID, ZclLevelControlCluster.CLUSTER_ID).collect(Collectors.toSet());
    }

    @Override
    public boolean initializeDevice() {
        if (initializeDeviceServer()) {
            logger.debug("{}: Level control device initialized as server", endpoint.getIeeeAddress());
            return true;
        }

        if (initializeDeviceClient()) {
            logger.debug("{}: Level control device initialized as client", endpoint.getIeeeAddress());
            return true;
        }

        logger.error("{}: Error initialising device", endpoint.getIeeeAddress());
        return false;
    }

    private boolean initializeDeviceServer() {
        ZclReportingConfig reporting = new ZclReportingConfig(channel);

        ZclLevelControlCluster serverClusterLevelControl = (ZclLevelControlCluster) endpoint
                .getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (serverClusterLevelControl == null) {
            logger.trace("{}: Error opening server device level controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterLevelControl).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverClusterLevelControl
                        .getAttribute(ZclLevelControlCluster.ATTR_CURRENTLEVEL);
                CommandResult reportingResponse = attribute
                        .setReporting(reporting.getReportingTimeMin(), reporting.getReportingTimeMax(), 1).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, reporting.getPollingPeriod());
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
                logger.debug("{}: Failed to bind level control cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error(
                    String.format("%s: Exception setting server level control reporting ", endpoint.getIeeeAddress()),
                    e);
            return false;
        }

        ZclOnOffCluster serverClusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (serverClusterOnOff == null) {
            logger.trace("{}: Error opening server device on off controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterOnOff).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverClusterOnOff.getAttribute(ZclOnOffCluster.ATTR_ONOFF);
                CommandResult reportingResponse = attribute
                        .setReporting(reporting.getReportingTimeMin(), reporting.getReportingTimeMax()).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, reporting.getPollingPeriod());
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
                logger.debug("{}: Failed to bind on off control cluster", endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error(String.format("%s: Exception setting server on off reporting ", endpoint.getIeeeAddress()), e);
            return false;
        }

        return true;
    }

    private boolean initializeDeviceClient() {
        ZclLevelControlCluster clusterLevelControl = (ZclLevelControlCluster) endpoint
                .getOutputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.trace("{}: Error opening client device level controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(clusterLevelControl).get();
            if (!bindResponse.isSuccess()) {
                logger.error("{}: Error 0x{} setting client binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error(
                    String.format("%s: Exception setting client level control reporting ", endpoint.getIeeeAddress()),
                    e);
            return false;
        }

        ZclOnOffCluster clusterOnOff = (ZclOnOffCluster) endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            logger.trace("{}: Error opening client device on off controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(clusterOnOff).get();
            if (!bindResponse.isSuccess()) {
                logger.error("{}: Error 0x{} setting client binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error(String.format("%s: Exception setting client on off reporting ", endpoint.getIeeeAddress()), e);
            return false;
        }

        return true;
    }

    @Override
    public synchronized boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);

        updateScheduler = Executors.newSingleThreadScheduledExecutor();

        if (initializeConverterServer()) {
            logger.debug("{}: Level control initialized as server", endpoint.getIeeeAddress());
            return true;
        }

        if (initializeConverterClient()) {
            logger.debug("{}: Level control initialized as client", endpoint.getIeeeAddress());
            return true;
        }

        logger.error("{}: Error opening device level controls", endpoint.getIeeeAddress());
        return false;
    }

    private boolean initializeConverterServer() {
        clusterLevelControlServer = (ZclLevelControlCluster) endpoint
                .getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControlServer == null) {
            logger.trace("{}: Error opening device level controls", endpoint.getIeeeAddress());
            return false;
        }

        clusterOnOffServer = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffServer == null) {
            logger.trace("{}: Error opening device on off controls", endpoint.getIeeeAddress());
            return false;
        }

        attributeOnOff = clusterOnOffServer.getAttribute(ZclOnOffCluster.ATTR_ONOFF);
        attributeLevel = clusterLevelControlServer.getAttribute(ZclLevelControlCluster.ATTR_CURRENTLEVEL);

        // Add a listeners
        clusterOnOffServer.addAttributeListener(this);
        clusterLevelControlServer.addAttributeListener(this);

        // Set the currentOnOffState to ON. This will ensure that we only ignore levelControl reports AFTER we have
        // really received an OFF report, thus confirming ON_OFF reporting is working
        currentOnOffState.set(true);

        // Create a configuration handler and get the available options
        configReporting = new ZclReportingConfig(channel);
        configLevelControl = new ZclLevelControlConfig();
        configLevelControl.initialize(clusterLevelControlServer);
        configOnOff = new ZclOnOffSwitchConfig();
        configOnOff.initialize(clusterOnOffServer);

        configOptions = new ArrayList<>();
        configOptions.addAll(configReporting.getConfiguration());
        configOptions.addAll(configLevelControl.getConfiguration());
        configOptions.addAll(configOnOff.getConfiguration());

        return true;
    }

    private boolean initializeConverterClient() {
        clusterLevelControlClient = (ZclLevelControlCluster) endpoint
                .getOutputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControlClient == null) {
            logger.trace("{}: Error opening device level controls", endpoint.getIeeeAddress());
            return false;
        }

        clusterOnOffClient = (ZclOnOffCluster) endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffClient == null) {
            logger.trace("{}: Error opening device on off controls", endpoint.getIeeeAddress());
            return false;
        }

        // Add a listeners
        clusterOnOffClient.addCommandListener(this);
        clusterLevelControlClient.addCommandListener(this);

        // Set the currentOnOffState to ON. This will ensure that we only ignore levelControl reports AFTER we have
        // really received an OFF report, thus confirming ON_OFF reporting is working
        currentOnOffState.set(true);

        configOptions = new ArrayList<>();

        return true;
    }

    @Override
    public void disposeConverter() {
        if (clusterOnOffClient != null) {
            clusterOnOffClient.removeCommandListener(this);
        }
        if (clusterLevelControlClient != null) {
            clusterLevelControlClient.removeCommandListener(this);
        }
        if (clusterOnOffServer != null) {
            clusterOnOffServer.removeAttributeListener(this);
        }
        if (clusterLevelControlServer != null) {
            clusterLevelControlServer.removeAttributeListener(this);
        }

        stopTransitionTimer();
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
        if (attributeOnOff != null) {
            attributeOnOff.readValue(0);
        }
        if (attributeLevel != null) {
            attributeLevel.readValue(0);
        }
    }

    @Override
    public void handleCommand(final Command command) {
        Command localCommand = command;
        Future<CommandResult> responseFuture = null;
        if (command instanceof OnOffType) {
            // TODO should this also be inverted
            responseFuture = handleOnOffCommand((OnOffType) command);
        } else if (command instanceof PercentType) {
            if (configLevelControl != null) {
                localCommand = configLevelControl.handleInvertControl(command);
            }
            responseFuture = handlePercentCommand((PercentType) localCommand);
        } else if (command instanceof IncreaseDecreaseType) {
            // TODO should this also be inverted
            responseFuture = handleIncreaseDecreaseCommand((IncreaseDecreaseType) command);
        } else {
            logger.warn("{}: Level converter only accepts PercentType, IncreaseDecreaseType and OnOffType - not {}",
                    endpoint.getIeeeAddress(), command.getClass().getSimpleName());
            return;
        }

        // Some functionality (eg IncreaseDecrease) requires that we know the last command received
        lastCommand = localCommand;
        monitorCommandResponse(localCommand, responseFuture, cmd -> {
            updateChannelState((State) cmd);
            if (cmd instanceof PercentType) {
                lastLevel = ((PercentType) cmd);
            }
        });
    }

    /**
     * If we support the OnOff cluster then we should perform the same function as the SwitchOnoffConverter. Otherwise,
     * interpret ON commands as moving to level 100%, and OFF commands as moving to level 0%.
     *
     * @return command result future
     */
    private Future<CommandResult> handleOnOffCommand(OnOffType cmdOnOff) {
        if (clusterOnOffServer != null) {
            ZclOnOffCommand onOffCommand;
            if (cmdOnOff == OnOffType.ON) {
                onOffCommand = new OnCommand();
            } else {
                onOffCommand = new OffCommand();
            }
            return clusterOnOffServer.sendCommand(onOffCommand);
        } else {
            if (cmdOnOff == OnOffType.ON) {
                return moveToLevel(PercentType.HUNDRED);
            } else {
                return moveToLevel(PercentType.ZERO);
            }
        }
    }

    private Future<CommandResult> handlePercentCommand(PercentType cmdPercent) {
        return moveToLevel(cmdPercent);
    }

    private Future<CommandResult> moveToLevel(PercentType percent) {
        ZclLevelControlCommand levelControlCommand = null;
        if (clusterOnOffServer != null) {
            if (percent.equals(PercentType.ZERO)) {
                return clusterOnOffServer.sendCommand(new OffCommand());
            } else {
                levelControlCommand = new MoveToLevelWithOnOffCommand(percentToLevel(percent),
                        configLevelControl.getDefaultTransitionTime());
            }
        } else {
            levelControlCommand = new MoveToLevelCommand(percentToLevel(percent),
                    configLevelControl.getDefaultTransitionTime());
        }

        return clusterLevelControlServer.sendCommand(levelControlCommand);
    }

    /**
     * The IncreaseDecreaseType in openHAB is defined as a STEP command. however we want to use this for the Move/Stop
     * command which is not available in openHAB.
     * When the first IncreaseDecreaseType is received, we send the Move command and start a timer to send the Stop
     * command when no further IncreaseDecreaseType commands are received.
     * We use the lastCommand to check if the current command is the same IncreaseDecreaseType, and if so we just
     * restart the timer.
     * When the timer times out and sends the Stop command, it also sets lastCommand to null.
     *
     * @param cmdIncreaseDecrease the command received
     */
    private Future<CommandResult> handleIncreaseDecreaseCommand(IncreaseDecreaseType cmdIncreaseDecrease) {
        ZclLevelControlCommand levelControlCommand = null;
        if (!cmdIncreaseDecrease.equals(lastCommand)) {
            switch (cmdIncreaseDecrease) {
                case INCREASE:
                    levelControlCommand = new MoveWithOnOffCommand(0, 50);
                case DECREASE:
                    levelControlCommand = new MoveWithOnOffCommand(1, 50);
                    break;
                default:
                    break;
            }
        }

        startStopTimer(INCREASEDECREASE_TIMEOUT);
        if (levelControlCommand != null) {
            return clusterLevelControlServer.sendCommand(levelControlCommand);
        }

        return null;
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID) == null
                && endpoint.getOutputCluster(ZclLevelControlCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Level control cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_SWITCH_LEVEL),
                        ZigBeeBindingConstants.ITEM_TYPE_DIMMER)
                .withType(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL)
                .withLabel(getDeviceTypeLabel(endpoint) + ": " + ZigBeeBindingConstants.CHANNEL_LABEL_SWITCH_LEVEL)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        if (configReporting != null) {
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

                    attribute = clusterLevelControlServer.getAttribute(ZclLevelControlCluster.ATTR_CURRENTLEVEL);
                    reportingResponse = attribute.setReporting(configReporting.getReportingTimeMin(),
                            configReporting.getReportingTimeMax(), configReporting.getReportingChange()).get();
                    handleReportingResponse(reportingResponse, configReporting.getPollingPeriod(),
                            configReporting.getReportingTimeMax());
                } catch (InterruptedException | ExecutionException e) {
                    logger.debug("{}: Level control exception setting reporting", endpoint.getIeeeAddress(), e);
                }
            }
        }

        if (configLevelControl != null) {
            configLevelControl.updateConfiguration(currentConfiguration, updatedParameters);
        }
        if (configOnOff != null) {
            configOnOff.updateConfiguration(currentConfiguration, updatedParameters);
        }
    }

    @Override
    public synchronized void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.LEVEL_CONTROL
                && attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
            lastLevel = levelToPercent((Integer) val);
            if (configLevelControl != null) {
                lastLevel = configLevelControl.handleInvertReport(lastLevel);
            }
            if (currentOnOffState.get()) {
                // Note that state is only updated if the current On/Off state is TRUE (ie ON)
                updateChannelState(lastLevel);
            }
        } else if (attribute.getClusterType() == ZclClusterType.ON_OFF
                && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            if (attribute.getLastValue() != null) {
                currentOnOffState.set((Boolean) val);
                updateChannelState(currentOnOffState.get() ? lastLevel : OnOffType.OFF);
            }
        }
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        logger.debug("{}: ZigBee command received {}", endpoint.getIeeeAddress(), command);

        // OnOff Cluster Commands
        if (command instanceof OnCommand) {
            currentOnOffState.set(true);
            lastLevel = PercentType.HUNDRED;
            updateChannelState(lastLevel);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof OnWithTimedOffCommand) {
            currentOnOffState.set(true);
            OnWithTimedOffCommand timedCommand = (OnWithTimedOffCommand) command;
            lastLevel = PercentType.HUNDRED;
            updateChannelState(lastLevel);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startOffTimer(timedCommand.getOnTime() * 100);
            return true;
        }
        if (command instanceof OffCommand) {
            currentOnOffState.set(false);
            lastLevel = PercentType.ZERO;
            updateChannelState(lastLevel);
            return true;
        }
        if (command instanceof ToggleCommand) {
            currentOnOffState.set(!currentOnOffState.get());
            lastLevel = currentOnOffState.get() ? PercentType.HUNDRED : PercentType.ZERO;
            updateChannelState(lastLevel);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof OffWithEffectCommand) {
            OffWithEffectCommand offEffect = (OffWithEffectCommand) command;
            startOffEffect(offEffect.getEffectIdentifier(), offEffect.getEffectVariant());
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }

        // LevelControl Cluster Commands
        if (command instanceof MoveToLevelCommand || command instanceof MoveToLevelWithOnOffCommand) {
            int time;
            int level;

            if (command instanceof MoveToLevelCommand) {
                MoveToLevelCommand levelCommand = (MoveToLevelCommand) command;
                time = levelCommand.getTransitionTime();
                level = levelCommand.getLevel();
            } else {
                MoveToLevelWithOnOffCommand levelCommand = (MoveToLevelWithOnOffCommand) command;
                time = levelCommand.getTransitionTime();
                level = levelCommand.getLevel();
            }
            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startTransitionTimer(time * 100, levelToPercent(level).doubleValue());
            return true;
        }
        if (command instanceof MoveCommand || command instanceof MoveWithOnOffCommand) {
            int mode;
            int rate;

            if (command instanceof MoveCommand) {
                MoveCommand levelCommand = (MoveCommand) command;
                mode = levelCommand.getMoveMode();
                rate = levelCommand.getRate();
            } else {
                MoveWithOnOffCommand levelCommand = (MoveWithOnOffCommand) command;
                mode = levelCommand.getMoveMode();
                rate = levelCommand.getRate();
            }

            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);

            // Get percent change per step period
            double stepRatePerSecond = levelToPercent(rate).doubleValue();
            double distance;

            if (mode == 0) {
                distance = 100.0 - lastLevel.doubleValue();
            } else {
                distance = lastLevel.doubleValue();
            }
            int transitionTime = (int) (distance / stepRatePerSecond * 1000);

            startTransitionTimer(transitionTime, mode == 0 ? 100.0 : 0.0);
            return true;
        }
        if (command instanceof StepCommand || command instanceof StepWithOnOffCommand) {
            int mode;
            int step;
            int time;

            if (command instanceof StepCommand) {
                StepCommand levelCommand = (StepCommand) command;
                mode = levelCommand.getStepMode();
                step = levelCommand.getStepSize();
                time = levelCommand.getTransitionTime();
            } else {
                StepWithOnOffCommand levelCommand = (StepWithOnOffCommand) command;
                mode = levelCommand.getStepMode();
                step = levelCommand.getStepSize();
                time = levelCommand.getTransitionTime();
            }

            double value;
            if (mode == 0) {
                value = lastLevel.doubleValue() + levelToPercent(step).doubleValue();
            } else {
                value = lastLevel.doubleValue() - levelToPercent(step).doubleValue();
            }
            if (value < 0.0) {
                value = 0.0;
            } else if (value > 100.0) {
                value = 100.0;
            }

            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startTransitionTimer(time * 100, value);
            return true;
        }
        if (command instanceof StopCommand || command instanceof StopWithOnOffCommand) {
            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            stopTransitionTimer();
            return true;
        }

        return false;
    }

    private void stopTransitionTimer() {
        if (updateTimer != null) {
            updateTimer.cancel(true);
            updateTimer = null;
        }
    }

    /**
     * Starts a timer to transition to finalState with transitionTime milliseconds. The state will be updated every
     * STATE_UPDATE_RATE milliseconds.
     *
     * @param transitionTime the number of milliseconds to move the finalState
     * @param finalState the final level to move to
     */
    private void startTransitionTimer(int transitionTime, double finalState) {
        stopTransitionTimer();

        logger.debug("{}: Level transition move to {} in {}ms", endpoint.getIeeeAddress(), finalState, transitionTime);
        final int steps = transitionTime / STATE_UPDATE_RATE;
        if (steps == 0) {
            logger.debug("{}: Level transition timer has 0 steps. Setting to {}.", endpoint.getIeeeAddress(),
                    finalState);
            lastLevel = new PercentType((int) finalState);
            currentOnOffState.set(finalState != 0);
            updateChannelState(lastLevel);
            return;
        }
        final double start = lastLevel.doubleValue();
        final double step = (finalState - lastLevel.doubleValue()) / steps;

        updateTimer = updateScheduler.scheduleAtFixedRate(new Runnable() {
            private int count = 0;
            private double state = start;

            @Override
            public void run() {
                state += step;
                if (state < 0.0) {
                    state = 0.0;
                } else if (state > 100.0) {
                    state = 100.0;
                }
                lastLevel = new PercentType((int) state);
                logger.debug("{}: Level transition timer {}/{} updating to {}", endpoint.getIeeeAddress(), count, steps,
                        lastLevel);
                currentOnOffState.set(state != 0);
                updateChannelState(lastLevel);

                if (state == 0.0 || state == 100.0 || ++count == steps) {
                    logger.debug("{}: Level transition timer complete", endpoint.getIeeeAddress());
                    updateTimer.cancel(true);
                    updateTimer = null;
                }
            }
        }, 0, STATE_UPDATE_RATE, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts a timer after which the state will be set to OFF
     *
     * @param delay the number of milliseconds to wait before setting the value to OFF
     */
    private void startOffTimer(int delay) {
        stopTransitionTimer();

        updateTimer = updateScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                logger.debug("{}: OnOff auto OFF timer expired", endpoint.getIeeeAddress());
                lastLevel = PercentType.ZERO;
                currentOnOffState.set(false);
                updateChannelState(OnOffType.OFF);
                updateTimer = null;
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts a timer to perform the off effect
     *
     * @param effectId the effect type
     * @param effectVariant the effect variant
     */
    private void startOffEffect(int effectId, int effectVariant) {
        stopTransitionTimer();

        int effect = effectId << 8 + effectVariant;

        switch (effect) {
            case 0x0002:
                // 50% dim down in 0.8 seconds then fade to off in 12 seconds
                break;

            case 0x0100:
                // 20% dim up in 0.5s then fade to off in 1 second
                break;

            default:
                logger.debug("{}: Off effect {} unknown", endpoint.getIeeeAddress(), String.format("%04", effect));

            case 0x0000:
                // Fade to off in 0.8 seconds
            case 0x0001:
                // No fade
                startTransitionTimer(800, 0.0);
                break;
        }
    }

    /**
     * Starts a timer after which the Stop command will be sent
     *
     * @param delay the number of milliseconds to wait before setting the value to OFF
     */
    private void startStopTimer(int delay) {
        stopTransitionTimer();

        updateTimer = updateScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                logger.debug("{}: IncreaseDecrease Stop timer expired", endpoint.getIeeeAddress());
                clusterLevelControlServer.sendCommand(new StopWithOnOffCommand());
                lastCommand = null;
                updateTimer = null;
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

}
