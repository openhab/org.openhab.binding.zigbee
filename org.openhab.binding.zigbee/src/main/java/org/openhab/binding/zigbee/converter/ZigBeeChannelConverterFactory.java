/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.util.Collection;

import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;

/**
 * The {@link ZigBeeChannelFactory} provides a factory for creating channel converters for the ZigBee binding.
 * <p>
 * The factory performs two functions -
 * <ul>
 * <li>gets a list of channels the thing supports. This uses methods in each converter to decide if they are supported.
 * <li>instantiates converters based on the channel UID.
 * </ul>
 *
 * @author Chris Jackson
 * @author Thomas Höfer - osgified the mechanism how converters are made available to the binding
 */
public interface ZigBeeChannelConverterFactory {

    /**
     * Gets a list of all channels supported by the {@link ZigBeeEndpoint}
     *
     * @param thingUID the {@link ThingUID} of the thing
     * @param endpoint the {@link ZigBeeEndpoint} to generate the channels for
     *
     * @return a collection of all channels supported by the {@link ZigBeeEndpoint}
     */
    Collection<Channel> getChannels(ThingUID thingUID, ZigBeeEndpoint endpoint);

    /**
     * Creates a channel converter for the requested {@link ChannelTypeUID}
     *
     * @param thingHandler       the {@link ZigBeeThingHandler} for this channel
     * @param channel            the {@link Channel} to create the converter for
     * @param coordinatorHandler the {@link ZigBeeCoordinatorHandler}
     * @param ieeeAddress        the {@link IeeeAddress} of the device
     * @param endpointId         the endpoint ID for this channel on the device
     * @return the {@link ZigBeeBaseChannelConverter} or null if the channel is not supported
     */
    ZigBeeBaseChannelConverter createConverter(ZigBeeThingHandler thingHandler, Channel channel,
            ZigBeeCoordinatorHandler coordinatorHandler, IeeeAddress ieeeAddress, int endpointId);
}
