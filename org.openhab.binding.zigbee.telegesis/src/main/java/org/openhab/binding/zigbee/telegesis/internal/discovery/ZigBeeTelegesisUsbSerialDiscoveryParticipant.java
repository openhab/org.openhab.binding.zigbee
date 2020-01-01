/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.telegesis.internal.discovery;

import static java.util.Arrays.asList;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CONFIGURATION_PORT;
import static org.openhab.binding.zigbee.telegesis.TelegesisBindingConstants.THING_TYPE_TELEGESIS;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.usbserial.UsbSerialDeviceInformation;
import org.eclipse.smarthome.config.discovery.usbserial.UsbSerialDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;

/**
 * Discovery for ZigBee USB dongles, integrated in Eclipse SmartHome's USB-serial discovery by implementing
 * a component of type {@link UsbSerialDiscoveryParticipant}.
 * <p/>
 * Currently, this {@link UsbSerialDiscoveryParticipant} supports three USB dongles built around
 * a SiLabs EM357 ZigBee chip and a CP210x USB to UART bridge.
 *
 * @author Henning Sudbrock - initial contribution
 */
@Component(service = UsbSerialDiscoveryParticipant.class)
public class ZigBeeTelegesisUsbSerialDiscoveryParticipant implements UsbSerialDiscoveryParticipant {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = new HashSet<>(asList(THING_TYPE_TELEGESIS));

    public static final int SILICON_LABS_USB_VENDOR_ID = 0x10c4;

    /**
     * For the {@link ZigBeeTelegesisUsbSerialDiscoveryParticipant#SILICON_LABS_USB_VENDOR_ID}, this USB product ID
     * is used for the Telegesis ZigBee dongle model ETRX3USB+8M, as well as for one version of the QIVICON ZigBee stick
     * (which is also produced by Telegesis).
     */
    public static final int QIVICON_DONGLE_V1_AND_TELEGESIS_USB_PRODUCT_ID = 0x8293;

    /**
     * For the {@link ZigBeeTelegesisUsbSerialDiscoveryParticipant#SILICON_LABS_USB_VENDOR_ID}, this USB product ID
     * is used for one version of the QIVICON ZigBee stick.
     */
    public static final int QIVICON_DONGLE_V2_USB_PRODUCT_ID = 0x89fb;

    private static final int QIVICON_DONGLE_BAUD_RATE = 19200;

    private static final String QIVICON_DONGLE_DEFAULT_LABEL = "Telegesis ZigBee Dongle";

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return SUPPORTED_THING_TYPES;
    }

    @Override
    public @Nullable DiscoveryResult createResult(UsbSerialDeviceInformation deviceInformation) {
        if (isQiviconTelegesisDongle(deviceInformation)) {
            return DiscoveryResultBuilder.create(createQiviconTelegesisDongleThingType(deviceInformation))
                    .withLabel(createQiviconTelegesisDongleLabel(deviceInformation))
                    .withRepresentationProperty(CONFIGURATION_PORT)
                    .withProperty(CONFIGURATION_PORT, deviceInformation.getSerialPort())
                    .build();
        } else {
            return null;
        }
    }

    @Override
    public @Nullable ThingUID getThingUID(UsbSerialDeviceInformation deviceInformation) {
        if (isQiviconTelegesisDongle(deviceInformation)) {
            return createQiviconTelegesisDongleThingType(deviceInformation);
        } else {
            return null;
        }
    }

    private boolean isQiviconTelegesisDongle(UsbSerialDeviceInformation deviceInformation) {
        return deviceInformation.getVendorId() == SILICON_LABS_USB_VENDOR_ID
                && (deviceInformation.getProductId() == QIVICON_DONGLE_V1_AND_TELEGESIS_USB_PRODUCT_ID
                        || deviceInformation.getProductId() == QIVICON_DONGLE_V2_USB_PRODUCT_ID);
    }

    private ThingUID createQiviconTelegesisDongleThingType(UsbSerialDeviceInformation deviceInformation) {
        return new ThingUID(THING_TYPE_TELEGESIS, deviceInformation.getSerialNumber());
    }

    private @Nullable String createQiviconTelegesisDongleLabel(UsbSerialDeviceInformation deviceInformation) {
        if (deviceInformation.getProduct() != null && !deviceInformation.getProduct().isEmpty()) {
            return deviceInformation.getProduct() + " (ZigBee USB dongle)";
        } else {
            return QIVICON_DONGLE_DEFAULT_LABEL;
        }
    }
}
