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

import java.util.Collection;
import java.util.Set;

import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;

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
 * @author Chris Jackson - initial contribution. Refactored to remove ThingHandler from createConverter
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
     * @param channel the {@link Channel} to create the converter for
     * @param coordinatorHandler the {@link ZigBeeCoordinatorHandler}
     * @param ieeeAddress the {@link IeeeAddress} of the device
     * @param endpointId the endpoint ID for this channel on the device
     * @return the {@link ZigBeeBaseChannelConverter} or null if the channel is not supported
     */
    ZigBeeBaseChannelConverter createConverter(Channel channel, ZigBeeCoordinatorHandler coordinatorHandler,
            IeeeAddress ieeeAddress, int endpointId);

    /**
     * Gets the cluster IDs that are supported by all converters known to the system
     *
     * @return Set of cluster IDs supported by the system
     */
    Set<Integer> getImplementedClientClusters();

    /**
     * Gets the cluster IDs that are supported by all converters known to the system
     *
     * @return Set of cluster IDs supported by the system
     */
    Set<Integer> getImplementedServerClusters();
}
