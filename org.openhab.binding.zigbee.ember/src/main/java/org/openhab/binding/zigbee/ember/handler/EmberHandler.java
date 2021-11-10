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
package org.openhab.binding.zigbee.ember.handler;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.ember.EmberBindingConstants;
import org.openhab.binding.zigbee.ember.internal.EmberConfiguration;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.firmware.Firmware;
import org.openhab.core.thing.binding.firmware.FirmwareUpdateHandler;
import org.openhab.core.thing.binding.firmware.ProgressCallback;
import org.openhab.core.thing.binding.firmware.ProgressStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EzspConfigId;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.ConcentratorConfig;
import com.zsmartsystems.zigbee.transport.ConcentratorType;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareCallback;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareStatus;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareUpdate;

/**
 * The {@link EmberHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
// @NonNullByDefault
public class EmberHandler extends ZigBeeCoordinatorHandler implements FirmwareUpdateHandler {

    private static final String ASH_RX_DAT = "ASH_RX_DAT";
    private static final String ASH_TX_DAT = "ASH_TX_DAT";
    private static final String ASH_RX_ACK = "ASH_RX_ACK";
    private static final String ASH_TX_ACK = "ASH_TX_ACK";
    private static final String ASH_RX_NAK = "ASH_RX_NAK";
    private static final String ASH_TX_NAK = "ASH_TX_NAK";

    private final Logger logger = LoggerFactory.getLogger(EmberHandler.class);

    private final SerialPortManager serialPortManager;

    private @Nullable ScheduledFuture<?> pollingJob;

    public EmberHandler(Bridge coordinator, SerialPortManager serialPortManager,
            ZigBeeChannelConverterFactory channelFactory) {
        super(coordinator, channelFactory);
        this.serialPortManager = serialPortManager;
    }

    @Override
    protected void initializeDongle() {
        logger.debug("Initializing ZigBee Ember serial bridge handler.");

        EmberConfiguration config = getConfigAs(EmberConfiguration.class);
        ZigBeeDongleEzsp dongle = createDongle(config);
        TransportConfig transportConfig = createTransportConfig(config);

        startZigBee(dongle, transportConfig, DefaultSerializer.class, DefaultDeserializer.class);
    }

    @Override
    protected void initializeDongleSpecific() {
        if (pollingJob == null) {
            Runnable pollingRunnable = new Runnable() {
                @Override
                public void run() {
                    if (zigbeeTransport == null) {
                        return;
                    }

                    ZigBeeDongleEzsp dongle = (ZigBeeDongleEzsp) zigbeeTransport;
                    Map<String, Long> counters = dongle.getCounters();
                    if (!counters.isEmpty()) {
                        if (isLinked(EmberBindingConstants.CHANNEL_RX_DAT)) {
                            updateState(EmberBindingConstants.CHANNEL_RX_DAT,
                                    new DecimalType(counters.get(ASH_RX_DAT)));
                        }
                        if (isLinked(EmberBindingConstants.CHANNEL_TX_DAT)) {
                            updateState(EmberBindingConstants.CHANNEL_TX_DAT,
                                    new DecimalType(counters.get(ASH_TX_DAT)));
                        }
                        if (isLinked(EmberBindingConstants.CHANNEL_RX_ACK)) {
                            updateState(EmberBindingConstants.CHANNEL_RX_ACK,
                                    new DecimalType(counters.get(ASH_RX_ACK)));
                        }
                        if (isLinked(EmberBindingConstants.CHANNEL_TX_ACK)) {
                            updateState(EmberBindingConstants.CHANNEL_TX_ACK,
                                    new DecimalType(counters.get(ASH_TX_ACK)));
                        }
                        if (isLinked(EmberBindingConstants.CHANNEL_RX_NAK)) {
                            updateState(EmberBindingConstants.CHANNEL_RX_NAK,
                                    new DecimalType(counters.get(ASH_RX_NAK)));
                        }
                        if (isLinked(EmberBindingConstants.CHANNEL_TX_NAK)) {
                            updateState(EmberBindingConstants.CHANNEL_TX_NAK,
                                    new DecimalType(counters.get(ASH_TX_NAK)));
                        }
                    }
                }
            };

            pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, 30, 30, TimeUnit.SECONDS);
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

    @Override
    public void updateFirmware(Firmware firmware, ProgressCallback progressCallback) {
        logger.debug("Ember coordinator: update firmware with {}", firmware.getVersion());

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.FIRMWARE_UPDATING);
        zigbeeTransport.shutdown();

        // Define the sequence of the firmware update so that external consumers can listen for the progress
        progressCallback.defineSequence(ProgressStep.DOWNLOADING, ProgressStep.TRANSFERRING, ProgressStep.UPDATING);

        ZigBeeTransportFirmwareUpdate firmwareUpdate = (ZigBeeTransportFirmwareUpdate) zigbeeTransport;
        firmwareUpdate.updateFirmware(firmware.getInputStream(), new ZigBeeTransportFirmwareCallback() {
            @Override
            public void firmwareUpdateCallback(ZigBeeTransportFirmwareStatus status) {
                logger.debug("Ember dongle firmware status: {}", status);
                switch (status) {
                    case FIRMWARE_UPDATE_STARTED:
                        // ProgressStep.DOWNLOADING
                        progressCallback.next();
                        break;
                    case FIRMWARE_TRANSFER_STARTED:
                        // ProgressStep.TRANSFERRING
                        progressCallback.next();
                        break;
                    case FIRMWARE_TRANSFER_COMPLETE:
                        // ProgressStep.UPDATING
                        progressCallback.next();
                        break;
                    case FIRMWARE_UPDATE_COMPLETE:
                        progressCallback.success();

                        // Restart the handler...
                        dispose();
                        initialize();
                        break;
                    case FIRMWARE_UPDATE_CANCELLED:
                        progressCallback.canceled();
                        break;
                    case FIRMWARE_UPDATE_FAILED:
                        progressCallback.failed(ZigBeeBindingConstants.FIRMWARE_FAILED);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    public void cancel() {
        logger.debug("Ember coordinator: cancel firmware update");
        ZigBeeTransportFirmwareUpdate firmwareUpdate = (ZigBeeTransportFirmwareUpdate) zigbeeTransport;
        firmwareUpdate.cancelUpdateFirmware();
    }

    @Override
    public boolean isUpdateExecutable() {
        // Always allow the firmware to be updated
        // Don't link this to online/offline as if the bootload fails, then the dongle
        // will always start in the bootloader. This will mean the dongle is always offline
        // but as long as we can open the serial port we should be able to bootload new
        // firmware.
        return true;
    }

    private ZigBeeDongleEzsp createDongle(EmberConfiguration config) {
        FlowControl flowControl;
        if (ZigBeeBindingConstants.FLOWCONTROL_CONFIG_HARDWARE_CTSRTS.equals(config.zigbee_flowcontrol)) {
            flowControl = FlowControl.FLOWCONTROL_OUT_RTSCTS;
        } else if (ZigBeeBindingConstants.FLOWCONTROL_CONFIG_SOFTWARE_XONXOFF.equals(config.zigbee_flowcontrol)) {
            flowControl = FlowControl.FLOWCONTROL_OUT_XONOFF;
        } else {
            flowControl = FlowControl.FLOWCONTROL_OUT_NONE;
        }

        ZigBeePort serialPort = new org.openhab.binding.zigbee.serial.ZigBeeSerialPort(serialPortManager,
                config.zigbee_port, config.zigbee_baud, flowControl);
        final ZigBeeDongleEzsp dongle = new ZigBeeDongleEzsp(serialPort);

        logger.debug("ZigBee Ember Coordinator opening Port:'{}' PAN:{}, EPAN:{}, Channel:{}", config.zigbee_port,
                Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

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

        logger.debug("Ember end device poll timeout set to ({} * 2^{}) = {} seconds", pollTimeoutValue,
                pollTimeoutShift, (int) (pollTimeoutValue * Math.pow(2, pollTimeoutShift)));
        dongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_END_DEVICE_POLL_TIMEOUT, pollTimeoutValue);
        dongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_END_DEVICE_POLL_TIMEOUT_SHIFT, pollTimeoutShift);

        return dongle;
    }

    private TransportConfig createTransportConfig(EmberConfiguration config) {
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
}
