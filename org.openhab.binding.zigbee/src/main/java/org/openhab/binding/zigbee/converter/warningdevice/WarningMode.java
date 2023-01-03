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
 * Possible values for the warning mode in a warning type.
 *
 * @author Henning Sudbrock - initial contribution
 */
public enum WarningMode {
    STOP(0),
    BURGLAR(1),
    FIRE(2),
    EMERGENCY(3),
    POLICE_PANIC(4),
    FIRE_PANIC(5),
    EMERGENCY_PANIC(6);

    private int value;

    WarningMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
