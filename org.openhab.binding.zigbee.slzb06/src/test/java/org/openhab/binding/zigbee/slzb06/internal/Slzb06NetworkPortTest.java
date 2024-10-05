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
package org.openhab.binding.zigbee.slzb06.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 *
 * @author Chris Jackson
 *
 */
public class Slzb06NetworkPortTest {
    @Test
    public void processReceivedData() {
        Slzb06NetworkPort port = new Slzb06NetworkPort(null, 0);

        byte[] chunk = new byte[Slzb06NetworkPort.RX_BUFFER_LEN - 1];
        byte[] extra = new byte[1];

        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = 0;
        }
        port.processReceivedData(chunk, chunk.length);

        extra[0] = 1;
        port.processReceivedData(extra, 1);

        for (int i = 0; i < chunk.length - 1; i++) {
            assertEquals(0, port.read(1));
        }
        assertEquals(1, port.read(1));
    }

    @Test
    public void processReceivedDataOverflow() {
        Slzb06NetworkPort port = new Slzb06NetworkPort(null, 0);

        byte[] chunk = new byte[Slzb06NetworkPort.RX_BUFFER_LEN];
        byte[] extra = new byte[1];

        for (int i = 0; i < Slzb06NetworkPort.RX_BUFFER_LEN; i++) {
            chunk[i] = 0;
        }
        port.processReceivedData(chunk, chunk.length);

        extra[0] = 1;
        port.processReceivedData(extra, 1);

        for (int i = 0; i < Slzb06NetworkPort.RX_BUFFER_LEN - 2; i++) {
            assertEquals(0, port.read(1));
        }
        assertEquals(1, port.read(1));
    }
}
