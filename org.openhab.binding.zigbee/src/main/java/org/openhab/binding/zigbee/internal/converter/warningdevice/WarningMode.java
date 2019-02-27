/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.warningdevice;

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
