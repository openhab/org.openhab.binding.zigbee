/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.tuya;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * The {@link TuyaBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Chris Jackson - Initial contribution
 */
@NonNullByDefault
public class TuyaBindingConstants {

    // List of Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_TUYA_BLIND_AM25 = new ThingTypeUID(ZigBeeBindingConstants.BINDING_ID,
            "tuya_am25");

    public static final String CHANNEL_NAME_TUYA_BUTTON = "tuyabutton";
    public static final String CHANNEL_LABEL_TUYA_BUTTON = "Button";
    public static final ChannelTypeUID CHANNEL_TUYA_BUTTON = new ChannelTypeUID("zigbee:tuya_button");

}
