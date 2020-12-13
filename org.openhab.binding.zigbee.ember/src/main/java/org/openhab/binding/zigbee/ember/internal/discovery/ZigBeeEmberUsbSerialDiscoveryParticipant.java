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
package org.openhab.binding.zigbee.ember.internal.discovery;

import static java.util.Arrays.asList;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CONFIGURATION_BAUD;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CONFIGURATION_FLOWCONTROL;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CONFIGURATION_PORT;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.FLOWCONTROL_CONFIG_SOFTWARE_XONXOFF;
import static org.openhab.binding.zigbee.ember.EmberBindingConstants.THING_TYPE_EMBER;

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
 * Currently, this {@link UsbSerialDiscoveryParticipant} supports the ZigBee dongle 'BV AV2010/10' from Bitron Video.
 *
 * @author Henning Sudbrock - initial contribution
 */
@Component(service = UsbSerialDiscoveryParticipant.class)
public class ZigBeeEmberUsbSerialDiscoveryParticipant implements UsbSerialDiscoveryParticipant {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = new HashSet<>(asList(THING_TYPE_EMBER));

    public static final int SILICON_LABS_USB_VENDOR_ID = 0x10c4;
    public static final int BITRON_VIDEO_2010_10_PRODUCT_ID = 0x8b34;
    private static final int BITRON_VIDEO_2010_10_BAUD_RATE = 57600;
    private static final String BITRON_VIDEO_2010_10_DEFAULT_LABEL = "Bitron Video AV2010/10 Ember ZigBee Dongle";

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return SUPPORTED_THING_TYPES;
    }

    @Override
    public @Nullable DiscoveryResult createResult(UsbSerialDeviceInformation deviceInformation) {
        if (isBitronVideoDongle(deviceInformation)) {
            return DiscoveryResultBuilder.create(createBitronVideoDongleThingType(deviceInformation))
                    .withLabel(createBitronVideoDongleLabel(deviceInformation))
                    .withRepresentationProperty(CONFIGURATION_PORT)
                    .withProperty(CONFIGURATION_PORT, deviceInformation.getSerialPort())
                    .withProperty(CONFIGURATION_BAUD, BITRON_VIDEO_2010_10_BAUD_RATE)
                    .withProperty(CONFIGURATION_FLOWCONTROL, FLOWCONTROL_CONFIG_SOFTWARE_XONXOFF).build();
        } else {
            return null;
        }
    }

    @Override
    public @Nullable ThingUID getThingUID(UsbSerialDeviceInformation deviceInformation) {
        if (isBitronVideoDongle(deviceInformation)) {
            return createBitronVideoDongleThingType(deviceInformation);
        } else {
            return null;
        }
    }

    private boolean isBitronVideoDongle(UsbSerialDeviceInformation deviceInformation) {
        return deviceInformation.getVendorId() == SILICON_LABS_USB_VENDOR_ID
                && deviceInformation.getProductId() == BITRON_VIDEO_2010_10_PRODUCT_ID;
    }

    private ThingUID createBitronVideoDongleThingType(UsbSerialDeviceInformation deviceInformation) {
        return new ThingUID(THING_TYPE_EMBER, deviceInformation.getSerialNumber());
    }

    private @Nullable String createBitronVideoDongleLabel(UsbSerialDeviceInformation deviceInformation) {
        if (deviceInformation.getProduct() != null && !deviceInformation.getProduct().isEmpty()) {
            return deviceInformation.getProduct() + " (ZigBee USB dongle)";
        } else {
            return BITRON_VIDEO_2010_10_DEFAULT_LABEL;
        }
    }
}
