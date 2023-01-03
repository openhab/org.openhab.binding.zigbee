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
package org.openhab.binding.zigbee.ember;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link EmberBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Chris Jackson - Initial contribution
 */
@NonNullByDefault
public class EmberBindingConstants {

    public static final String BINDING_ID = "zigbee";

    public static final String CHANNEL_RX_DAT = "rx_dat";
    public static final String CHANNEL_TX_DAT = "tx_dat";
    public static final String CHANNEL_RX_ACK = "rx_ack";
    public static final String CHANNEL_TX_ACK = "tx_ack";
    public static final String CHANNEL_RX_NAK = "rx_nak";
    public static final String CHANNEL_TX_NAK = "tx_nak";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_EMBER = new ThingTypeUID(BINDING_ID, "coordinator_ember");

}
