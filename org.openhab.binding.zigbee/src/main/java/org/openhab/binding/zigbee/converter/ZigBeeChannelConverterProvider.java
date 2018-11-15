/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;

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
