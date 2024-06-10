/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.slzb06.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.slzb06.Slzb06BindingConstants;
import org.openhab.binding.zigbee.slzb06.internal.Slzb06Configuration;
import org.openhab.binding.zigbee.slzb06.internal.Slzb06NetworkPort;
import org.openhab.binding.zigbee.slzb06.internal.api.Slzb06Communicator;
import org.openhab.binding.zigbee.slzb06.internal.api.Slzb06Exception;
import org.openhab.binding.zigbee.slzb06.internal.api.Slzb06SensorsOuter.Slzb06Sensors;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Bridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.dongle.ember.EmberNcp;
import com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberMulticastTableEntry;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberStatus;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EzspConfigId;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.ConcentratorConfig;
import com.zsmartsystems.zigbee.transport.ConcentratorType;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.ZigBeePort;

/**
 * The {@link Slzb06Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public class Slzb06Handler extends ZigBeeCoordinatorHandler {
    private static final String ASH_RX_DAT = "ASH_RX_DAT";
    private static final String ASH_TX_DAT = "ASH_TX_DAT";
    private static final String ASH_RX_ACK = "ASH_RX_ACK";
    private static final String ASH_TX_ACK = "ASH_TX_ACK";
    private static final String ASH_RX_NAK = "ASH_RX_NAK";
    private static final String ASH_TX_NAK = "ASH_TX_NAK";

    /**
     * Sets the minimum size of the multicast address table
     */
    private static final int GROUPS_MINIMUM = 3;

    /**
     * Sets the minimum headroom in the multicast table. This allows for room for the table to grow during a session.
     * Note that the table size is set only on startup since this impacts the Ember memory use.
     */
    private static final int GROUPS_HEADROOM = 3;

    /**
     * Sets the maximum size of the groups table. This is designed to be large enough to allow pretty much any
     * configuration, but small enough that to protect against the user adding loads of random numbers or invalid
     * formatting of the config string.
     */
    private static final int GROUPS_MAXIMUM = 20;

    private final Logger logger = LoggerFactory.getLogger(Slzb06Handler.class);

    private @Nullable ScheduledFuture<?> pollingJob;

    private Slzb06Communicator communicator;

    public Slzb06Handler(Bridge coordinator, ZigBeeChannelConverterFactory channelFactory) {
        super(coordinator, channelFactory);
    }

    @Override
    protected void initializeDongle() {
        logger.debug("Initializing ZigBee SLZB06 network bridge handler.");

        Slzb06Configuration config = getConfigAs(Slzb06Configuration.class);
        ZigBeeDongleEzsp dongle = createDongle(config);
        TransportConfig transportConfig = createTransportConfig(config);

        try {
            communicator = new Slzb06Communicator(config.slzb06_server);
        } catch (Slzb06Exception e) {
            logger.error("SLZB06 API failed to initialise - internal channels will be unavailable: {}", e.getMessage());
        }

        startZigBee(dongle, transportConfig, DefaultSerializer.class, DefaultDeserializer.class);
    }

    @Override
    protected void initializeDongleSpecific() {
        Slzb06Configuration config = getConfigAs(Slzb06Configuration.class);
        setGroupRegistration(config.zigbee_groupregistration);

        if (pollingJob == null) {
            Runnable pollingRunnable = new Runnable() {
                @Override
                public void run() {
                    if (zigbeeTransport != null) {
                        ZigBeeDongleEzsp dongle = (ZigBeeDongleEzsp) zigbeeTransport;
                        Map<String, Long> counters = dongle.getCounters();
                        if (!counters.isEmpty()) {
                            if (isLinked(Slzb06BindingConstants.CHANNEL_RX_DAT)) {
                                updateState(Slzb06BindingConstants.CHANNEL_RX_DAT,
                                        new DecimalType(counters.get(ASH_RX_DAT)));
                            }
                            if (isLinked(Slzb06BindingConstants.CHANNEL_TX_DAT)) {
                                updateState(Slzb06BindingConstants.CHANNEL_TX_DAT,
                                        new DecimalType(counters.get(ASH_TX_DAT)));
                            }
                            if (isLinked(Slzb06BindingConstants.CHANNEL_RX_ACK)) {
                                updateState(Slzb06BindingConstants.CHANNEL_RX_ACK,
                                        new DecimalType(counters.get(ASH_RX_ACK)));
                            }
                            if (isLinked(Slzb06BindingConstants.CHANNEL_TX_ACK)) {
                                updateState(Slzb06BindingConstants.CHANNEL_TX_ACK,
                                        new DecimalType(counters.get(ASH_TX_ACK)));
                            }
                            if (isLinked(Slzb06BindingConstants.CHANNEL_RX_NAK)) {
                                updateState(Slzb06BindingConstants.CHANNEL_RX_NAK,
                                        new DecimalType(counters.get(ASH_RX_NAK)));
                            }
                            if (isLinked(Slzb06BindingConstants.CHANNEL_TX_NAK)) {
                                updateState(Slzb06BindingConstants.CHANNEL_TX_NAK,
                                        new DecimalType(counters.get(ASH_TX_NAK)));
                            }
                        }
                    }

                    if (communicator == null) {
                        try {
                            communicator = new Slzb06Communicator(config.slzb06_server);
                        } catch (Slzb06Exception e) {
                            communicator = null;
                            logger.error("SLZB06 API failed to initialise - internal channels will be unavailable: {}",
                                    e.getMessage());
                        }
                    }

                    if (communicator != null) {
                        try {
                            Slzb06Sensors sensors = communicator.getSensors();
                            if (isLinked(Slzb06BindingConstants.CHANNEL_ESP32TEMP) && sensors.esp32_temp != null) {
                                updateState(Slzb06BindingConstants.CHANNEL_ESP32TEMP,
                                        new DecimalType(sensors.esp32_temp));
                            }
                            if (isLinked(Slzb06BindingConstants.CHANNEL_ZBTEMP) && sensors.zb_temp != null) {
                                updateState(Slzb06BindingConstants.CHANNEL_ZBTEMP, new DecimalType(sensors.zb_temp));
                            }
                            if (isLinked(Slzb06BindingConstants.CHANNEL_UPTIME) && sensors.uptime != null) {
                                updateState(Slzb06BindingConstants.CHANNEL_UPTIME, new DecimalType(sensors.uptime));
                            }
                            if (isLinked(Slzb06BindingConstants.CHANNEL_SOCKETUPTIME)
                                    && sensors.socket_uptime != null) {
                                updateState(Slzb06BindingConstants.CHANNEL_SOCKETUPTIME,
                                        new DecimalType(sensors.socket_uptime / 1000));
                            }
                        } catch (Exception e) {
                            logger.error("SLZB06: retreiving API information: {}", e.getMessage());
                        }
                    }

                }
            };

            pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, 30, 30, TimeUnit.SECONDS);
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("{}: SLZB06 configuration received.", nodeIeeeAddress);

        Map<String, Object> unhandledConfiguration = new HashMap<>();
        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            // Ignore any configuration parameters that have not changed
            if (Objects.equals(configurationParameter.getValue(), configuration.get(configurationParameter.getKey()))) {
                logger.debug("{}: SLZB06 configuration update: Ignored {} as no change", nodeIeeeAddress,
                        configurationParameter.getKey());
                continue;
            }

            logger.debug("{}: SLZB06 configuration update: Processing {} -> {}", nodeIeeeAddress,
                    configurationParameter.getKey(), configurationParameter.getValue());

            switch (configurationParameter.getKey()) {
                case ZigBeeBindingConstants.CONFIGURATION_GROUPREGISTRATION:
                    setGroupRegistration((String) configurationParameter.getValue());
                    break;
                default:
                    unhandledConfiguration.put(configurationParameter.getKey(), configurationParameter.getValue());
                    break;
            }

            configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
        }

        if (!unhandledConfiguration.isEmpty()) {
            super.handleConfigurationUpdate(unhandledConfiguration);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        if (pollingJob != null) {
            pollingJob.cancel(true);
            pollingJob = null;
        }
    }

    private ZigBeeDongleEzsp createDongle(Slzb06Configuration config) {

        ZigBeePort networkPort = new Slzb06NetworkPort(config.slzb06_server, config.slzb06_port);
        final ZigBeeDongleEzsp dongle = new ZigBeeDongleEzsp(networkPort);

        logger.debug("ZigBee SLZB06 Coordinator opening Port:'{}:{}' PAN:{}, EPAN:{}, Channel:{}", config.slzb06_server,
                config.slzb06_port, Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

        dongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_ADDRESS_TABLE_SIZE, config.zigbee_networksize);

        dongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_TX_POWER_MODE, config.zigbee_powermode);

        // We don't need to receive broadcast and multicast messages that we send
        dongle.passLoopbackMessages(false);

        // Set the child aging timeout.
        // Formulae is EZSP_CONFIG_END_DEVICE_POLL_TIMEOUT * 2 ^ EZSP_CONFIG_END_DEVICE_POLL_TIMEOUT_SHIFT
        int pollTimeoutValue;
        int pollTimeoutShift;

        if (config.zigbee_childtimeout <= 320) {
            pollTimeoutValue = 5;
            pollTimeoutShift = 6;
        } else if (config.zigbee_childtimeout <= 1800) {
            pollTimeoutValue = 225;
            pollTimeoutShift = 3;
        } else if (config.zigbee_childtimeout <= 7200) {
            pollTimeoutValue = 225;
            pollTimeoutShift = 5;
        } else if (config.zigbee_childtimeout <= 43200) {
            pollTimeoutValue = 169;
            pollTimeoutShift = 8;
        } else if (config.zigbee_childtimeout <= 86400) {
            pollTimeoutValue = 169;
            pollTimeoutShift = 9;
        } else if (config.zigbee_childtimeout <= 172800) {
            pollTimeoutValue = 169;
            pollTimeoutShift = 10;
        } else if (config.zigbee_childtimeout <= 432000) {
            pollTimeoutValue = 211;
            pollTimeoutShift = 11;
        } else if (config.zigbee_childtimeout <= 864000) {
            pollTimeoutValue = 211;
            pollTimeoutShift = 12;
        } else if (config.zigbee_childtimeout <= 1209600) {
            pollTimeoutValue = 147;
            pollTimeoutShift = 13;
        } else if (config.zigbee_childtimeout <= 2419200) {
            pollTimeoutValue = 147;
            pollTimeoutShift = 14;
        } else {
            pollTimeoutValue = 255;
            pollTimeoutShift = 14;
        }

        logger.debug("ZigBee SLZB06 end device poll timeout set to ({} * 2^{}) = {} seconds", pollTimeoutValue,
                pollTimeoutShift, (int) (pollTimeoutValue * Math.pow(2, pollTimeoutShift)));
        dongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_END_DEVICE_POLL_TIMEOUT, pollTimeoutValue);
        dongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_END_DEVICE_POLL_TIMEOUT_SHIFT, pollTimeoutShift);

        return dongle;
    }

    private TransportConfig createTransportConfig(Slzb06Configuration config) {
        TransportConfig transportConfig = new TransportConfig();

        // Configure the concentrator
        // Max Hops defaults to system max
        ConcentratorConfig concentratorConfig = new ConcentratorConfig();
        if (config.zigbee_concentrator == 1) {
            concentratorConfig.setType(ConcentratorType.HIGH_RAM);
        } else {
            concentratorConfig.setType(ConcentratorType.LOW_RAM);
        }
        concentratorConfig.setMaxFailures(8);
        concentratorConfig.setMaxHops(0);
        concentratorConfig.setRefreshMinimum(60);
        concentratorConfig.setRefreshMaximum(3600);
        transportConfig.addOption(TransportConfigOption.CONCENTRATOR_CONFIG, concentratorConfig);
        return transportConfig;
    }

    private void setGroupRegistration(String groupsString) {
        if (groupsString == null || groupsString.isBlank()) {
            return;
        }

        logger.debug("ZigBee SLZB06 Coordinator group registration is {}", groupsString);
        ZigBeeDongleEzsp dongle = (ZigBeeDongleEzsp) zigbeeTransport;
        EmberNcp ncp = dongle.getEmberNcp();

        String[] groupsArray = groupsString.split(",");
        Set<Integer> groups = new HashSet<>();

        for (String groupString : groupsArray) {
            groups.add(Integer.parseInt(groupString.trim(), 16));
        }

        int multicastTableSize = groups.size() + GROUPS_HEADROOM;
        if (multicastTableSize < GROUPS_MINIMUM) {
            multicastTableSize = GROUPS_MINIMUM;
        } else if (multicastTableSize > GROUPS_MAXIMUM) {
            multicastTableSize = GROUPS_MAXIMUM;
        }
        logger.debug("ZigBee SLZB06 Coordinator multicast table size set to {}", multicastTableSize);
        dongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_MULTICAST_TABLE_SIZE, multicastTableSize);

        int index = 0;
        for (Integer group : groups) {
            EmberMulticastTableEntry entry = new EmberMulticastTableEntry();
            entry.setEndpoint(1);
            entry.setNetworkIndex(0);
            entry.setMulticastId(group);
            EmberStatus result = ncp.setMulticastTableEntry(index, entry);

            logger.debug("ZigBee SLZB06 Coordinator multicast table index {} updated with {}, result {}", index, entry,
                    result);

            index++;
        }
    }
}
