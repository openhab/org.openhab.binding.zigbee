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
package org.openhab.binding.zigbee.cc2531;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link CC2531BindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Chris Jackson - Initial contribution
 */
@NonNullByDefault
public class CC2531BindingConstants {

    private static final String BINDING_ID = "zigbee";

    public final static String CONFIGURATION_ZNP_MAGICNUMBER = "zigbee_znp_magicnumber";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_CC2531 = new ThingTypeUID(BINDING_ID, "coordinator_cc2531");

}
