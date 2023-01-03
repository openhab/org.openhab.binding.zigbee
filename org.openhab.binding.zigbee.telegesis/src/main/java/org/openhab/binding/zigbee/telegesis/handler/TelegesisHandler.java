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
package org.openhab.binding.zigbee.telegesis.handler;

import java.util.HashSet;
import java.util.Set;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.serial.ZigBeeSerialPort;
import org.openhab.binding.zigbee.telegesis.internal.TelegesisConfiguration;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.firmware.Firmware;
import org.openhab.core.thing.binding.firmware.FirmwareUpdateHandler;
import org.openhab.core.thing.binding.firmware.ProgressCallback;
import org.openhab.core.thing.binding.firmware.ProgressStep;
import org.osgi.service.component.annotations.Activate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.dongle.telegesis.ZigBeeDongleTelegesis;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareCallback;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareStatus;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareUpdate;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;

/**
 * The {@link TelegesisHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public class TelegesisHandler extends ZigBeeCoordinatorHandler implements FirmwareUpdateHandler {
    private final Logger logger = LoggerFactory.getLogger(TelegesisHandler.class);

    private final SerialPortManager serialPortManager;

    @Activate
    public TelegesisHandler(Bridge coordinator, SerialPortManager serialPortManager,
            ZigBeeChannelConverterFactory channelFactory) {
        super(coordinator, channelFactory);
        this.serialPortManager = serialPortManager;
    }

    @Override
    protected void initializeDongle() {
        logger.debug("Initializing ZigBee Telegesis serial bridge handler.");

        TelegesisConfiguration config = getConfigAs(TelegesisConfiguration.class);
        ZigBeeDongleTelegesis dongle = createDongle(config);
        TransportConfig transportConfig = createTransportConfig();

        startZigBee(dongle, transportConfig, DefaultSerializer.class, DefaultDeserializer.class);
    }

    @Override
    public void thingUpdated(Thing thing) {
        super.thingUpdated(thing);
    }

    @Override
    public void updateFirmware(Firmware firmware, ProgressCallback progressCallback) {
        logger.debug("Telegesis coordinator: update firmware with {}", firmware.getVersion());

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.FIRMWARE_UPDATING);
        zigbeeTransport.shutdown();

        // Define the sequence of the firmware update so that external consumers can listen for the progress
        progressCallback.defineSequence(ProgressStep.DOWNLOADING, ProgressStep.TRANSFERRING, ProgressStep.UPDATING);

        ZigBeeTransportFirmwareUpdate firmwareUpdate = (ZigBeeTransportFirmwareUpdate) zigbeeTransport;
        firmwareUpdate.updateFirmware(firmware.getInputStream(), new ZigBeeTransportFirmwareCallback() {
            @Override
            public void firmwareUpdateCallback(ZigBeeTransportFirmwareStatus status) {
                logger.debug("Telegesis dongle firmware status: {}", status);
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
        logger.debug("Telegesis coordinator: cancel firmware update");
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

    private ZigBeeDongleTelegesis createDongle(TelegesisConfiguration config) {
        ZigBeePort serialPort = new ZigBeeSerialPort(serialPortManager, config.zigbee_port, config.zigbee_baud,
                FlowControl.FLOWCONTROL_OUT_NONE);
        final ZigBeeDongleTelegesis dongle = new ZigBeeDongleTelegesis(serialPort);

        logger.debug("ZigBee Telegesis Coordinator opening Port:'{}' PAN:{}, EPAN:{}, Channel:{}", config.zigbee_port,
                Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

        dongle.setTelegesisPassword(config.zigbee_password);
        return dongle;
    }

    private TransportConfig createTransportConfig() {
        TransportConfig transportConfig = new TransportConfig();

        // The Telegesis dongle doesn't pass the MatchDescriptor commands to the stack, so we can't manage our services
        // directly. Instead, register any services we want to support so the Telegesis can handle the MatchDescriptor.
        Set<Integer> clusters = new HashSet<Integer>();
        clusters.add(ZclIasZoneCluster.CLUSTER_ID);
        transportConfig.addOption(TransportConfigOption.SUPPORTED_OUTPUT_CLUSTERS, clusters);
        return transportConfig;
    }

}
