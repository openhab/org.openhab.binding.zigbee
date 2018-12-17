/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.telegesis.handler;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.firmware.Firmware;
import org.eclipse.smarthome.core.thing.binding.firmware.FirmwareUpdateHandler;
import org.eclipse.smarthome.core.thing.binding.firmware.ProgressCallback;
import org.eclipse.smarthome.core.thing.binding.firmware.ProgressStep;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeSerialPort;
import org.openhab.binding.zigbee.telegesis.internal.TelegesisConfiguration;
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
// @NonNullByDefault
public class TelegesisHandler extends ZigBeeCoordinatorHandler implements FirmwareUpdateHandler {
    private final Logger logger = LoggerFactory.getLogger(TelegesisHandler.class);

    public TelegesisHandler(Bridge coordinator) {
        super(coordinator);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee Telegesis serial bridge handler.");

        // Call the parent to finish any global initialisation
        super.initialize();

        TelegesisConfiguration config = getConfigAs(TelegesisConfiguration.class);

        ZigBeePort serialPort = new ZigBeeSerialPort(config.zigbee_port, config.zigbee_baud,
                FlowControl.FLOWCONTROL_OUT_NONE);
        final ZigBeeDongleTelegesis dongle = new ZigBeeDongleTelegesis(serialPort);

        logger.debug("ZigBee Telegesis Coordinator opening Port:'{}' PAN:{}, EPAN:{}, Channel:{}", config.zigbee_port,
                Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

        dongle.setTelegesisPassword(config.zigbee_password);

        TransportConfig transportConfig = new TransportConfig();

        // The Telegesis dongle doesn't pass the MatchDescriptor commands to the stack, so we can't manage our services
        // directly. Instead, register any services we want to support so the Telegesis can handle the MatchDescriptor.
        Set<Integer> clusters = new HashSet<Integer>();
        clusters.add(ZclIasZoneCluster.CLUSTER_ID);
        transportConfig.addOption(TransportConfigOption.SUPPORTED_OUTPUT_CLUSTERS, clusters);

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
}
