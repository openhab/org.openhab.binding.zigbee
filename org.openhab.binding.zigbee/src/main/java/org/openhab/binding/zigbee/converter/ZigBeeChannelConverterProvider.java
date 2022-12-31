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
package org.openhab.binding.zigbee.converter;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * The {@link ZigBeeChannelConverterProvider} can be registered as OSGi service in order to make additional
 * {@link ZigBeeBaseChannelConverter}s available to the binding.
 *
 * @author Thomas Höfer - Initial contribution
 */
@NonNullByDefault
public interface ZigBeeChannelConverterProvider {

    /**
     * Provides a map of {@link ChannelTypeUID}s and their corresponding channel converters.
     *
     * @return all channel converters of this provider
     */
    Map<ChannelTypeUID, Class<? extends ZigBeeBaseChannelConverter>> getChannelConverters();
}
