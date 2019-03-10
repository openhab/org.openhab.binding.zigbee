/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
