/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.cc2531;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

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
