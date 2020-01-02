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
import org.openhab.binding.zigbee.internal.ZigBeeCommandParametersImpl;

import java.util.Collection;
import java.util.Optional;

/**
 * The {@link ZigBeeCommandParameters} interface represents a collection of parameters to pass to a ZigBee command.
 *
 * @author Thomas Weißschuh - Initial contribution
 */
@NonNullByDefault
public interface ZigBeeCommandParameters {
    static ZigBeeCommandParameters empty() {
        return new ZigBeeCommandParametersImpl();
    }

    <T> ZigBeeCommandParameters add(final ZigBeeCommandParameter<T> param, final T value);
    <T> Optional<T> get(final ZigBeeCommandParameter<T> param);
    Collection<ZigBeeCommandParameter<?>> setParameters();
}
