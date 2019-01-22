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
 * Possible siren level values.
 *
 * @author Henning Sudbrock - initial contribution
 */
public enum SirenLevel {

    LOW(0),
    MEDIUM(1),
    HIGH(2),
    VERY_HIGH(3);

    private int value;

    SirenLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

}
