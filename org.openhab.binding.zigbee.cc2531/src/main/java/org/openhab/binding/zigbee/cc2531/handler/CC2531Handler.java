/**
 * Copyright (c) 2014,2018 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.zigbee.cc2531.handler;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.firmware.Firmware;
import org.eclipse.smarthome.core.thing.binding.firmware.FirmwareUpdateHandler;
import org.eclipse.smarthome.core.thing.binding.firmware.ProgressCallback;
import org.eclipse.smarthome.core.thing.binding.firmware.ProgressStep;
import org.openhab.binding.zigbee.cc2531.internal.CC2531Configuration;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeSerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareCallback;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareStatus;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareUpdate;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;

/**
 * The {@link CC2531Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
// @NonNullByDefault
public class CC2531Handler extends ZigBeeCoordinatorHandler implements FirmwareUpdateHandler {
    private final Logger logger = LoggerFactory.getLogger(CC2531Handler.class);

    public CC2531Handler(Bridge coordinator) {
        super(coordinator);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee CC2531 serial bridge handler.");

        // Call the parent to finish any global initialisation
        super.initialize();

        CC2531Configuration config = getConfigAs(CC2531Configuration.class);

        ZigBeePort serialPort = new ZigBeeSerialPort(config.zigbee_port, config.zigbee_baud,
                FlowControl.FLOWCONTROL_OUT_RTSCTS);
        final ZigBeeTransportTransmit dongle = new ZigBeeDongleTiCc2531(serialPort);

        logger.debug("ZigBee CC2531 Coordinator opening Port:'{}' PAN:{}, EPAN:{}, Channel:{}", config.zigbee_port,
                Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

        TransportConfig transportConfig = new TransportConfig();

        startZigBee(dongle, transportConfig, DefaultSerializer.class, DefaultDeserializer.class);
    }

    @Override
    public void updateFirmware(Firmware firmware, ProgressCallback progressCallback) {
        logger.debug("CC2531 coordinator: update firmware with {}", firmware.getVersion());

        updateStatus(ThingStatus.OFFLINE);
        zigbeeTransport.shutdown();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.FIRMWARE_UPDATING);

        // Define the sequence of the firmware update so that external consumers can listen for the progress
        progressCallback.defineSequence(ProgressStep.DOWNLOADING, ProgressStep.TRANSFERRING, ProgressStep.UPDATING);

        ZigBeeTransportFirmwareUpdate firmwareUpdate = (ZigBeeTransportFirmwareUpdate) zigbeeTransport;
        firmwareUpdate.updateFirmware(firmware.getInputStream(), new ZigBeeTransportFirmwareCallback() {
            @Override
            public void firmwareUpdateCallback(ZigBeeTransportFirmwareStatus status) {
                logger.debug("CC2531 dongle firmware status: {}", status);
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
                        progressCallback.failed("zigbee.firmware.failed");
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    public void cancel() {
        logger.debug("CC2531 coordinator: cancel firmware update");
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
