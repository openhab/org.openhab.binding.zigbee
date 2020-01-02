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
package org.openhab.binding.zigbee;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ZigBeeCommandParameter} class represents a single pre-defined parameter to a ZigBee command.
 *
 * @author Thomas Weißschuh - Initial contribution
 */
@NonNullByDefault
public final class ZigBeeCommandParameter<T> {
    private final String name;
    private final Class<T> type;

    public static final ZigBeeCommandParameter<Integer> TRANSITION_TIME =
            new ZigBeeCommandParameter<>(Integer.class, "transitionTime");

    ZigBeeCommandParameter(final Class<T> type, final String name) {
        this.type = type;
        this.name = name;
    }

    public Class<T> getType() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }
}
