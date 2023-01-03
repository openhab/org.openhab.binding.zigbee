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
package org.openhab.binding.zigbee.converter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.StateDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeDeviceType;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeProfileType;
import com.zsmartsystems.zigbee.zcl.ZclCluster;

/**
 * ZigBeeBaseChannelConverter class. Base class for all converters that convert between ZigBee clusters, attributes and
 * commands, and ESH channels.
 * <p>
 * The following converter lifecycle is defined -:
 * <ul>
 * <li>The thing handler may call {@link #getChannel(ThingUID, ZigBeeEndpoint)} to check if the device supports a
 * channel from the converter. The converter should check the features of the {@link ZigBeeEndpoint} to see if the
 * required features are available. If so, it should return the channel. This method allows dynamic channel detection
 * for unknown devices and may not be called if a device is defined through another mechanism.
 * <li>The thing handler will call the
 * {@link #initialize(ZigBeeThingHandler, Channel, ZigBeeCoordinatorHandler, IeeeAddress, int)} to initialise the
 * channel
 * converter. This simply links the converter to the node endpoint.
 * <li>The thing handler will call {@link #initializeDevice()} to initialise the device. This method may not be called
 * every time the converter is created, and may only be called when the device is first installed on the network, or
 * when the device needs to be reconfigured. It should configure the binding by calling {@link ZclCluster#bind()} and
 * configure reporting for attributes that are required to maintain state, along with any other one time device/channel
 * specific configuration that may be required.
 * <li>The thing handler will call {@link #initializeConverter()} to initialise the converter. The converter should get
 * any clusters via the {@link ZigBeeCoordinatorHandler#getEndpoint(IeeeAddress, int)} and
 * {@link ZigBeeEndpoint#getInputCluster(int)} or {@link ZigBeeEndpoint#getOutputCluster(int)}. It should configure any
 * listeners for Attribute changes or incoming commands. During the initialisation, the converter should populate
 * {@link #configOptions} with any configuration options that the device supports that may need to be adjusted by the
 * user.
 * <li>If the converter receives information from the ZigBee library that updates the channel state, it should update
 * the state by calling {@link #updateChannelState(State)}.
 * <li>The thing handler may call {@link #updateConfiguration(Configuration)} with an updated channel configuration from
 * the user. The handler should update the configuration in the device, read back the updated configuration if
 * necessary, and return an updated channel configuration to the thing handler.
 * <li>The thing handler may call {@link #handleCommand(Command)} if there is an incoming command.
 * <li>The thing handler may call {@link #handleRefresh()} to poll for an update of the channel data.
 * <li>The thing handler will call {@link #disposeConverter()} when the channel is no longer required. The converter
 * must release all resources an unregister any listeners from the ZigBee library.
 * </ul>
 * <p>
 * Since many interactions with ZigBee devices can take an appreciable time to complete (especially for battery
 * devices, most of the above commands are run in separate threads. The thread management is handled in the
 * {@link ThingHandler} to allow common thread pools to be used so the converter does not need to be concerned with this
 * other than to be careful about thread synchronisation. A converter should not assume it has exclusive access to a
 * cluster as related functions may also be utilising the cluster.
 *
 * @author Chris Jackson
 * @author Thomas Höfer - osgified the mechanism how converters are made available to the binding
 */
public abstract class ZigBeeBaseChannelConverter {
    /**
     * Our logger
     */
    private final Logger logger = LoggerFactory.getLogger(ZigBeeBaseChannelConverter.class);

    private final static BigDecimal TEMPERATURE_MULTIPLIER = new BigDecimal(100);

    /**
     * Default minimum reporting period. Should be short to ensure we get dynamic state changes in a reasonable time
     */
    protected final int REPORTING_PERIOD_DEFAULT_MIN = 1;

    /**
     * Default maximum reporting period
     */
    protected final int REPORTING_PERIOD_DEFAULT_MAX = 7200;

    /**
     * Default polling period (in seconds).
     */
    protected final int POLLING_PERIOD_DEFAULT = 7200;

    /**
     * Standard high rate polling period (in seconds).
     */
    protected final int POLLING_PERIOD_HIGH = 60;

    /**
     * The {@link ZigBeeThingHandler} to which this channel belongs.
     */
    protected ZigBeeThingHandler thing = null;

    /**
     * The {@link ZigBeeCoordinatorHandler} that controls the network
     */
    protected ZigBeeCoordinatorHandler coordinator = null;

    /**
     * The {@link StateDescription} or null if there are no descriptions for this channel
     */
    protected StateDescription stateDescription = null;

    /**
     * A List of {@link ConfigDescriptionParameter} supported by this channel. This should be populated during the
     * {@link #initializeConverter()} method if the device has configuration the user needs to be concerned with.
     */
    protected List<ConfigDescriptionParameter> configOptions = null;

    /**
     * The channel
     */
    protected Channel channel = null;

    /**
     * The {@link ChannelUID} for this converter
     */
    protected ChannelUID channelUID = null;

    /**
     * The {@link ZigBeeEndpoint} this channel is linked to
     */
    protected ZigBeeEndpoint endpoint = null;

    /**
     * The polling period used for this channel in seconds. Normally this should be left at the default
     * ({@link ZigBeeBaseChannelConverter#POLLING_PERIOD_DEFAULT}), however if the channel does not support reporting,
     * it can be set to a higher period such as {@link ZigBeeBaseChannelConverter#POLLING_PERIOD_HIGH}. Any period may
     * be used, however it is recommended to use these standard settings.
     */
    protected int pollingPeriod = POLLING_PERIOD_DEFAULT;

    /**
     * The smallest of all maximum reporting periods configured for the attributes used by the converter. By default it
     * is set to {@code Integer.MAX_VALUE}, because we assume that not every converter implements reporting.
     */
    protected int minimalReportingPeriod = Integer.MAX_VALUE;

    /**
     * Constructor. Creates a new instance of the {@link ZigBeeBaseChannelConverter} class.
     *
     */
    public ZigBeeBaseChannelConverter() {
        super();
    }

    /**
     * Creates the converter handler.
     *
     * @param channel the {@link Channel} for the channel
     * @param coordinator the {@link ZigBeeCoordinatorHandler} this endpoint is part of
     * @param address the {@link IeeeAddress} of the node
     * @param endpointId the endpoint this channel is linked to
     */
    public void initialize(Channel channel, ZigBeeCoordinatorHandler coordinator, IeeeAddress address, int endpointId) {
        this.endpoint = coordinator.getEndpoint(address, endpointId);
        if (this.endpoint == null) {
            throw new IllegalArgumentException("Endpoint was not found");
        }
        this.channel = channel;
        this.channelUID = channel.getUID();
        this.coordinator = coordinator;
    }

    /**
     * Configures the device. This method should perform the one off device configuration such as performing the bind
     * and reporting configuration.
     * <p>
     * The binding should initialize reporting using one of the {@link ZclCluster#setReporting} commands. If this fails,
     * the {@link #pollingPeriod} variable should be set to {@link #POLLING_PERIOD_HIGH}.
     * <p>
     * Note that this method should be self contained, and may not make any assumptions about the initialization of any
     * internal fields of the converter other than those initialized in the {@link #initialize} method.
     *
     * @return true if the device was configured correctly
     */
    public boolean initializeDevice() {
        return true;
    }

    /**
     * Gets the cluster IDs that are implemented within the converter on the client side.
     *
     * @return Set of cluster IDs supported by the converter
     */
    public abstract Set<Integer> getImplementedClientClusters();

    /**
     * Gets the cluster IDs that are implemented within the converter on the server side.
     *
     * @return Set of cluster IDs supported by the converter
     */
    public abstract Set<Integer> getImplementedServerClusters();

    /**
     * Initialise the converter. This is called by the {@link ZigBeeThingHandler} when the channel is created. The
     * converter should initialise any internal states, open any clusters, add reporting and binding that it needs to
     * operate.
     * <p>
     * A list of configuration parameters for the thing should be built and added to {@link #configOptions} based on the
     * features the device supports.
     *
     * @param thing the {@link ZigBeeThingHandler} the channel is part of
     * @return true if the converter was initialised successfully
     */
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        this.thing = thing;
        return false;
    }

    /**
     * Closes the converter and releases any resources.
     */
    public void disposeConverter() {
        // Overridable if the converter has cleanup to perform
    }

    /**
     * Execute refresh method. This method is called every time a binding item is refreshed and the corresponding node
     * should be sent a message.
     * <p>
     * This is run in a separate thread by the Thing Handler so the converter doesn't need to worry about returning
     * quickly.
     */
    public void handleRefresh() {
        // Overridable if a channel can be refreshed
    }

    /**
     * Receives a command from openHAB and translates it to an operation on the ZigBeee network.
     * <p>
     * This is run in a separate thread by the Thing Handler so the converter doesn't need to worry about returning
     * quickly.
     *
     * @param command the {@link Command} to send
     */
    public void handleCommand(final Command command) {
        // Overridable if a channel can be commanded
    }

    /**
     * Creates a {@link Channel} if this converter supports features from the {@link ZigBeeEndpoint}
     * If the converter doesn't support any features, it returns null.
     * <p>
     * The converter should perform the following -:
     * <ul>
     * <li>Check if the device supports the cluster(s) required by the converter
     * <li>Check if the cluster supports the attributes or commands required by the converter
     * </ul>
     * Only if the device supports the features required by the channel should the channel be implemented.
     *
     * @param thingUID the {@link ThingUID} of the thing to which the channel will be attached
     * @param endpoint The {@link ZigBeeEndpoint} to search for channels
     * @return a {@link Channel} if the converter supports features from the {@link ZigBeeEndpoint}, otherwise null.
     */
    public abstract Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint);

    /**
     * Updates the channel state within the thing.
     *
     * @param state the updated {@link State}
     */
    protected void updateChannelState(State state) {
        logger.debug("{}: Channel {} updated to {}", endpoint.getIeeeAddress(), channelUID, state);

        thing.setChannelState(channelUID, state);
    }

    /**
     * Gets the configuration descriptions required to configure this channel.
     * <p>
     * Ideally, implementations should use the {@link ZclCluster#discoverAttributes(boolean)} method and the
     * {@link ZclCluster#isAttributeSupported(int)} method to understand exactly what the device supports and only
     * provide configuration as necessary.
     * <p>
     * This method should not be overridden - the {@link #configOptions} list should be populated during converter
     * initialisation.
     *
     * @return a {@link List} of {@link ConfigDescriptionParameter}s. null if no config is provided
     */
    public List<ConfigDescriptionParameter> getConfigDescription() {
        return configOptions;
    }

    /**
     * Gets the {@link StateDescription} for this channel
     *
     * @return the {@link StateDescription} for this channel, or null if no state description is provided
     */
    public StateDescription getStateDescription() {
        return stateDescription;
    }

    /**
     * Gets the polling period for this channel in seconds. Normally this should be left at the default
     * ({@link ZigBeeBaseChannelConverter#POLLING_PERIOD_DEFAULT}), however if the channel does not support reporting,
     * it can be set to a higher period such as {@link ZigBeeBaseChannelConverter#POLLING_PERIOD_HIGH} during the
     * converter initialisation. Any period may be used, however it is recommended to use these standard settings.
     *
     * @return the polling period for this channel in seconds
     */
    public int getPollingPeriod() {
        return pollingPeriod;
    }

    /**
     * Gets the minimum of all maximum reporting periods used in attribute reports. If no attribute reports are
     * configured, the default {@code Integer.MAX_VALUE} will be returned.
     *
     * @return minimum of all maximum reporting periods in seconds
     */
    public int getMinimalReportingPeriod() {
        return minimalReportingPeriod;
    }

    /**
     * Sets the {@code pollingPeriod} and {@code maxReportingPeriod} depending on the success or failure of the given
     * reporting response.
     *
     * @param reportResponse a {@link CommandResult} representing the response to a reporting request
     * @param reportingFailedPollingInterval the polling interval to be used in case configuring reporting has
     *            failed
     * @param reportingSuccessMaxReportInterval the maximum reporting interval in case reporting is successfully
     *            configured
     */
    protected void handleReportingResponse(CommandResult reportResponse, int reportingFailedPollingInterval,
            int reportingSuccessMaxReportInterval) {
        if (!reportResponse.isSuccess()) {
            // We want the minimum of all pollingPeriods
            pollingPeriod = Math.min(pollingPeriod, reportingFailedPollingInterval);
        } else {
            // We want to know the minimum of all maximum reporting periods to be used as a timeout value
            minimalReportingPeriod = Math.min(minimalReportingPeriod, reportingSuccessMaxReportInterval);
        }
    }

    /**
     * Creates a standard channel UID given the {@link ZigBeeEndpoint}
     *
     * @param thingUID the {@link ThingUID}
     * @param endpoint the {@link ZigBeeEndpoint}
     * @param channelName the name of the channel
     * @return
     */
    protected ChannelUID createChannelUID(ThingUID thingUID, ZigBeeEndpoint endpoint, String channelName) {
        return new ChannelUID(thingUID, endpoint.getIeeeAddress() + "_" + endpoint.getEndpointId() + "_" + channelName);
    }

    /**
     * Creates a set of properties, adding the standard properties required by the system.
     * Channel converters may add additional properties prior to creating the channel.
     *
     * @param endpoint the {@link ZigBeeEndpoint}
     * @return an initial properties map
     */
    protected Map<String, String> createProperties(ZigBeeEndpoint endpoint) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(ZigBeeBindingConstants.CHANNEL_PROPERTY_ENDPOINT, Integer.toString(endpoint.getEndpointId()));

        return properties;
    }

    /**
     * Converts a ZigBee 8 bit level as used in Level Control cluster and others to a percentage
     *
     * @param level an integer between 0 and 254
     * @return the scaled {@link PercentType}
     */
    protected PercentType levelToPercent(int level) {
        return new PercentType((int) (level * 100.0 / 254.0 + 0.5));
    }

    /**
     * Converts a {@link PercentType} to an 8 bit level scaled between 0 and 254
     *
     * @param percent the {@link PercentType} to convert
     * @return a scaled value between 0 and 254
     */
    protected int percentToLevel(PercentType percent) {
        return (int) (percent.floatValue() * 254.0f / 100.0f + 0.5f);
    }

    /**
     * Converts an integer value into a {@link QuantityType}. The temperature as an integer is assumed to be multiplied
     * by 100 as per the ZigBee standard format.
     *
     * @param value the integer value to convert
     * @return the {@link QuantityType}
     */
    protected QuantityType valueToTemperature(int value) {
        return new QuantityType<>(BigDecimal.valueOf(value, 2), SIUnits.CELSIUS);
    }

    /**
     * Converts an 0-100 numeric value into a Percentage {@link QuantityType}.
     *
     * @param value the integer value to convert
     * @return the {@link QuantityType}
     */
    protected QuantityType valueToPercentDimensionless(Number value) {
        return new QuantityType<>(value, Units.PERCENT);
    }

    /**
     * Gets a {@link String} of the device type for the {@link ZigBeeEndpoint} to be used in device labels.
     *
     * @param endpoint the {@link ZigBeeEndpoint}
     * @return the {@link String} of the device type
     */
    protected String getDeviceTypeLabel(ZigBeeEndpoint endpoint) {
        ZigBeeProfileType profileType = ZigBeeProfileType.getByValue(endpoint.getProfileId());
        if (profileType == null) {
            profileType = ZigBeeProfileType.ZIGBEE_HOME_AUTOMATION;
        }

        ZigBeeDeviceType deviceType = ZigBeeDeviceType.getByValue(profileType, endpoint.getDeviceId());

        if (deviceType == null) {
            return String.format("Unknown Device Type %04X", endpoint.getDeviceId());
        }

        return deviceType.toString();
    }

    /**
     * Converts a {@link Command} to a ZigBee temperature integer
     *
     * @param command the {@link Command} to convert
     * @return the {@link Command} or null if the conversion was not possible
     */
    protected Integer temperatureToValue(Command command) {
        BigDecimal value = null;
        if (command instanceof QuantityType) {
            QuantityType<?> quantity = (QuantityType<?>) command;
            if (quantity.getUnit() == SIUnits.CELSIUS) {
                value = quantity.toBigDecimal();
            } else if (quantity.getUnit() == ImperialUnits.FAHRENHEIT) {
                QuantityType<?> celsius = quantity.toUnit(SIUnits.CELSIUS);
                if (celsius == null) {
                    return null;
                }
                value = celsius.toBigDecimal();
            } else {
                return null;
            }
        } else if (command instanceof Number) {
            // No scale, so assumed to be Celsius
            value = BigDecimal.valueOf(((Number) command).doubleValue());
        }
        return value.setScale(2, RoundingMode.CEILING).multiply(TEMPERATURE_MULTIPLIER).intValue();
    }

    /**
     * Processes the updated configuration. As required, the method shall process each known configuration parameter and
     * set a local variable for local parameters, and update the remote device for remote parameters.
     * The currentConfiguration shall be updated.
     * <p>
     * This must not be called before the {@link #initializeConverter()} method has been called.
     *
     * @param currentConfiguration the current {@link Configuration}
     * @param updatedParameters a map containing the updated configuration parameters to be set
     */
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        // Nothing required in default implementation
    }

    /**
     * Creates a binding from the remote cluster to the local {@link ZigBeeProfileType#ZIGBEE_HOME_AUTOMATION} endpoint
     *
     * @param cluster the remote {@link ZclCluster} to bind to
     * @return the future {@link CommandResult}
     */
    protected Future<CommandResult> bind(ZclCluster cluster) {
        return cluster.bind(coordinator.getLocalIeeeAddress(),
                coordinator.getLocalEndpointId(ZigBeeProfileType.ZIGBEE_HOME_AUTOMATION));
    }

    /**
     * Monitors the command response.
     * <ul>
     * <li>If the command fails (timeout), then we set the thing OFFLINE.
     * <li>If the command succeeds, we wait for an attribute report to update the state
     * <li>If there is no attribute report received, then we set the state to the original command (if applicable)
     * </ul>
     * <p>
     * Note that this is called from a separate thread created in the ThingHandler, so we can safely block here.
     *
     * @param command the {@link Command} that was sent from the framework
     * @param future the response from the sendCommand method when sending a command (may be null)
     */
    protected void monitorCommandResponse(final Command command, final Future<CommandResult> future) {
        if (future == null) {
            return;
        }
        monitorCommandResponse(command, Collections.singletonList(future));
    }

    /**
     * Monitors the command response.
     * <ul>
     * <li>If the command fails (timeout), then we set the thing OFFLINE.
     * <li>If the command succeeds, we wait for an attribute report to update the state
     * <li>If there is no attribute report received, then we set the state to the original command (if applicable)
     * </ul>
     * <p>
     * Note that this is called from a separate thread created in the ThingHandler, so we can safely block here.
     *
     * @param command the {@link Command} that was sent from the framework
     * @param future the response from the sendCommand method when sending a command (may be null)
     * @param completionFunction the expression to be called on successful completion
     */
    protected void monitorCommandResponse(Command command, final Future<CommandResult> future,
            Consumer<Command> completionFunction) {
        if (future == null) {
            return;
        }
        monitorCommandResponse(command, Collections.singletonList(future), completionFunction);
    }

    /**
     * Monitors the command response.
     * <ul>
     * <li>If the command fails (timeout), then we set the thing OFFLINE.
     * <li>If the command succeeds, we wait for an attribute report to update the state
     * <li>If there is no attribute report received, then we set the state to the original command (if applicable)
     * </ul>
     * <p>
     * Note that this is called from a separate thread created in the ThingHandler, so we can safely block here.
     *
     * @param command the OH command that is being sent
     * @param futures the list of futures to wait for for the ZCL commands being sent to the device
     */
    protected void monitorCommandResponse(Command command, List<Future<CommandResult>> futures) {
        monitorCommandResponse(command, futures, cmd -> {
            updateChannelState((State) cmd);
        });
    }

    /**
     * Monitors the command response.
     * <ul>
     * <li>If the command fails (timeout), then we set the thing OFFLINE.
     * <li>If the command succeeds, we wait for an attribute report to update the state
     * <li>If there is no attribute report received, then we set the state to the original command (if applicable)
     * </ul>
     * <p>
     * Note that this is called from a separate thread created in the ThingHandler, so we can safely block here.
     *
     * @param command the OH command that is being sent
     * @param futures the list of futures to wait for for the ZCL commands being sent to the device
     * @param completionFunction the expression to be called on successful completion
     */
    protected void monitorCommandResponse(Command command, List<Future<CommandResult>> futures,
            Consumer<Command> completionFunction) {
        try {
            logger.debug("{}: Channel {} waiting for response to {}", endpoint.getIeeeAddress(), channelUID, command);
            for (Future<CommandResult> future : futures) {
                if (future == null) {
                    continue;
                }
                CommandResult response = future.get();
                if (response.isTimeout()) {
                    logger.debug("{}: Channel {} received TIMEOUT in response to {}", endpoint.getIeeeAddress(),
                            channelUID, command);
                    thing.aliveTimeoutReached();
                    return;
                }
                if (response.isError()) {
                    logger.debug("{}: Channel {} received ERROR in response to {}", endpoint.getIeeeAddress(),
                            channelUID, command);
                    return;
                }
            }
            // No commands timed out or errored
            logger.debug("{}: Channel {} received SUCCESS in response to {}", endpoint.getIeeeAddress(), channelUID,
                    command);

            // Treat a successful response as confirmation the device is in the commanded state
            // This might not be 100% correct, but if the device doesn't send the report, then things can get messy, so
            // this is a good compromise.
            completionFunction.accept(command);

            thing.alive();
        } catch (InterruptedException | ExecutionException e) {
        }
    }
}
