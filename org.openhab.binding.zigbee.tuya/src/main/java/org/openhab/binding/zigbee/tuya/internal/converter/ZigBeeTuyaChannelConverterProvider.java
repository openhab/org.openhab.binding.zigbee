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
package org.openhab.binding.zigbee.tuya.internal.converter;

import java.util.HashMap;
import java.util.Map;

import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterProvider;
import org.openhab.binding.zigbee.tuya.TuyaBindingConstants;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.osgi.service.component.annotations.Component;

/**
 * The base {@link ZigBeeTuyaChannelConverterProvider} of the binding making the Tuya specific
 * {@link ZigBeeBaseChannelConverter}s available for the {@link ZigBeeChannelConverterFactory}.
 *
 * @author Chris Jackson - Initial contribution
 */
@Component(immediate = true, service = ZigBeeChannelConverterProvider.class)
public final class ZigBeeTuyaChannelConverterProvider implements ZigBeeChannelConverterProvider {

    private final Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> channelMap = new HashMap<>();

    public ZigBeeTuyaChannelConverterProvider() {
        // Add all the converters into the map...
        channelMap.put(TuyaBindingConstants.CHANNEL_TUYA_BUTTON, ZigBeeConverterTuyaButton.class);
    }

    @Override
    public Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> getChannelConverters() {
        return channelMap;
    }
}
