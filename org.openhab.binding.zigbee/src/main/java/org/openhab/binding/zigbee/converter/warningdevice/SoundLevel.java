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
package org.openhab.binding.zigbee.converter.warningdevice;

/**
 * Possible siren level values (for both warning and squawk commands).
 *
 * @author Henning Sudbrock - initial contribution
 */
public enum SoundLevel {

    LOW(0),
    MEDIUM(1),
    HIGH(2),
    VERY_HIGH(3);

    private int value;

    SoundLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

}
