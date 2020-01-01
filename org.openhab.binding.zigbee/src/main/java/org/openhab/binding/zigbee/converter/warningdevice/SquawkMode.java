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
package org.openhab.binding.zigbee.converter.warningdevice;

/**
 * Possible values for the squawk mode in a squawk type.
 *
 * @author Henning Sudbrock - initial contribution
 */
public enum SquawkMode {
    ARMED(0),
    DISARMED(1);

    private int value;

    SquawkMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
