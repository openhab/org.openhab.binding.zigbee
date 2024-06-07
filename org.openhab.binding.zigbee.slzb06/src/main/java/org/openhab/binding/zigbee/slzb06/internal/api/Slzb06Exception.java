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
package org.openhab.binding.zigbee.slzb06.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link Slzb06Exception} class defines an exception for handling SLZB06 exceptions
 *
 * @author Chris Jackson - Initial contribution
 */

@NonNullByDefault
public class Slzb06Exception extends Exception {
    private static final long serialVersionUID = 2247293108913709712L;

    public Slzb06Exception(String message) {
        super(message);
    }
}
