/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CONFIGURATION_BAUD;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CONFIGURATION_PORT;
import static org.openhab.binding.zigbee.ember.EmberBindingConstants.THING_TYPE_EMBER;
import static org.openhab.binding.zigbee.ember.internal.discovery.ZigBeeEmberUsbSerialDiscoveryParticipant.BITRON_VIDEO_2010_10_PRODUCT_ID;
import static org.openhab.binding.zigbee.ember.internal.discovery.ZigBeeEmberUsbSerialDiscoveryParticipant.SILICON_LABS_USB_VENDOR_ID;

import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.usbserial.UsbSerialDeviceInformation;
import org.openhab.core.thing.ThingUID;
import org.junit.Before;
import org.junit.Test;
import org.openhab.binding.zigbee.ember.EmberBindingConstants;
import org.openhab.binding.zigbee.ember.internal.discovery.ZigBeeEmberUsbSerialDiscoveryParticipant;

/**
 * Unit tests for the {@link ZigBeeEmberUsbSerialDiscoveryParticipant}.
 */
public class ZigBeeEmberUsbSerialDiscoveryParticipantTest {

    private ZigBeeEmberUsbSerialDiscoveryParticipant discoveryParticipant;
    
    @Before
    public void setup() {
        discoveryParticipant = new ZigBeeEmberUsbSerialDiscoveryParticipant();
    }
    
    /**
     * If only USB vendor ID or only USB product ID or none of them matches, then no device is discovered. 
     */
    @Test
    public void testNonEmberDongleNotDiscovered() {
        assertNull(discoveryParticipant.getThingUID(forUsbDongle(SILICON_LABS_USB_VENDOR_ID, 0x1234)));
        assertNull(discoveryParticipant.getThingUID(forUsbDongle(0xabcd, BITRON_VIDEO_2010_10_PRODUCT_ID)));
        assertNull(discoveryParticipant.getThingUID(forUsbDongle(0xabcd, 0x1234)));
        
        assertNull(discoveryParticipant.createResult(forUsbDongle(SILICON_LABS_USB_VENDOR_ID, 0x1234)));
        assertNull(discoveryParticipant.createResult(forUsbDongle(0xabcd, BITRON_VIDEO_2010_10_PRODUCT_ID)));
        assertNull(discoveryParticipant.createResult(forUsbDongle(0xabcd, 0x1234)));
    }
    
    /**
     * For matching USB vendor and product ID, a suitable thingUID is returned.
     */
    @Test
    public void testEmberDongleDiscoveredThingUID() {
        ThingUID thingUID = discoveryParticipant.getThingUID(
                forUsbDongle(SILICON_LABS_USB_VENDOR_ID, BITRON_VIDEO_2010_10_PRODUCT_ID, "serial", "/dev/ttyUSB0"));
        
        assertNotNull(thingUID);
        assertEquals(thingUID, new ThingUID(THING_TYPE_EMBER, "serial"));
    }
    
    /**
     * For matching USB vendor and product ID, a suitable discovery result is returned.
     */
    @Test
    public void testEmberDongleDiscoveredDiscoveryResult() {
        DiscoveryResult discoveryResult = discoveryParticipant.createResult(
                forUsbDongle(SILICON_LABS_USB_VENDOR_ID, BITRON_VIDEO_2010_10_PRODUCT_ID, "serial", "/dev/ttyUSB0"));
        
        assertNotNull(discoveryResult);
        assertEquals(discoveryResult.getThingUID(), new ThingUID(THING_TYPE_EMBER, "serial"));
        assertNotNull(discoveryResult.getLabel());
        assertEquals(discoveryResult.getRepresentationProperty(), CONFIGURATION_PORT);
        assertEquals(discoveryResult.getProperties().get(CONFIGURATION_PORT), "/dev/ttyUSB0");
        assertNotNull(discoveryResult.getProperties().get(CONFIGURATION_BAUD));
    }
    
    private UsbSerialDeviceInformation forUsbDongle(int vendorId, int productId, String serial, String device) {
        return new UsbSerialDeviceInformation(vendorId, productId, serial, null, null, 0, null, device);
    }

    private UsbSerialDeviceInformation forUsbDongle(int vendorId, int productId) {
        return new UsbSerialDeviceInformation(vendorId, productId, null, null, null, 0, null, "/dev/ttyUSB0");
    }
    
}
