/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.StateDescription;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.IeeeAddress;
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
 * {@link ZigBeeBaseChannelConverter#initialize(ZigBeeThingHandler, Channel, ZigBeeCoordinatorHandler, IeeeAddress, int)}
 * to initialise the
 * channel converter. The converter should get any clusters via the
 * {@link ZigBeeCoordinatorHandler#getEndpoint(IeeeAddress, int)} and {@link ZigBeeEndpoint#getInputCluster(int)} or
 * {@link ZigBeeEndpoint#getOutputCluster(int)}. It should configure the binding by calling {@link ZclCluster#bind()}
 * and configure reporting for attributes that are required to maintain state. It should configure any listeners for
 * Attribute changes or incoming commands. During the initialisation, the converter should populate
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
    private Logger logger = LoggerFactory.getLogger(ZigBeeBaseChannelConverter.class);

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
     * it can be set to a higher
     * period such as {@link ZigBeeBaseChannelConverter#POLLING_PERIOD_HIGH}. Any period may be used, however it is
     * recommended to use these
     * standard settings.
     */
    protected int pollingPeriod = POLLING_PERIOD_DEFAULT;

    /**
     * Constructor. Creates a new instance of the {@link ZigBeeBaseChannelConverter} class.
     *
     */
    public ZigBeeBaseChannelConverter() {
        super();
    }

    /**
     * Creates the converter handler
     *
     * @param thing the {@link ZigBeeThingHandler} the channel is part of
     * @param channel the {@link Channel} for the channel
     * @param coordinator the {@link ZigBeeCoordinatorHandler} this endpoint is part of
     * @param address the {@link IeeeAddress} of the node
     * @param endpointId the endpoint this channel is linked to
     */
    public void initialize(ZigBeeThingHandler thing, Channel channel, ZigBeeCoordinatorHandler coordinator,
            IeeeAddress address, int endpointId) {
        this.endpoint = coordinator.getEndpoint(address, endpointId);
        if (this.endpoint == null) {
            throw new IllegalArgumentException("Device was not found");
        }
        this.thing = thing;
        this.channel = channel;
        this.channelUID = channel.getUID();
        this.coordinator = coordinator;
    }

    /**
     * Initialise the converter. This is called by the {@link ZigBeeThingHandler} when the channel is created. The
     * converter should initialise any internal states, open any clusters, add reporting and binding that it needs to
     * operate.
     * <p>
     * The binding should initialise reporting using one of the {@link ZclCluster#setReporting} commands. If this fails,
     * the {@link #pollingPeriod} variable should be set to {@link #POLLING_PERIOD_HIGH}.
     * <p>
     * A list of configuration parameters for the thing should be built and added to {@link #configOptions} based on the
     * features the device supports.
     *
     * @return true if the converter was initialised successfully
     */
    public abstract boolean initializeConverter();

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
     * provide
     * configuration as necessary.
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
     * it can be set to a higher
     * period such as {@link ZigBeeBaseChannelConverter#POLLING_PERIOD_HIGH} during the converter initialisation. Any
     * period may be used, however
     * it is recommended to use these standard settings.
     *
     * @return the polling period for this channel in seconds
     */
    public int getPollingPeriod() {
        return pollingPeriod;
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
     * Update the channel configuration. This is called by the Thing Handler if the user updates the channel
     * configuration.
     *
     * @param configuration the channel {@link Configuration}
     * @return the updated {@link Configuration} to persist to handler configuration
     */
    public Configuration updateConfiguration(@NonNull Configuration configuration) {
        // Nothing required as default implementation
        return new Configuration();
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
}
