/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.type.DynamicStateDescriptionProvider;
import org.eclipse.smarthome.core.types.StateDescription;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic channel state description provider.
 * Overrides the state description for the controls, which receive its configuration in the runtime.
 *
 * @author Chris Jackson
 *
 */
@NonNullByDefault
@Component(service = { DynamicStateDescriptionProvider.class,
        ZigBeeDynamicStateDescriptionProvider.class }, immediate = true)
public class ZigBeeDynamicStateDescriptionProvider implements DynamicStateDescriptionProvider {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeDynamicStateDescriptionProvider.class);

    private final Map<ChannelUID, StateDescription> descriptions = new ConcurrentHashMap<>();

    /**
     * Set a state description for a channel. This description will be used when preparing the channel state by
     * the framework for presentation. Any previous description, will be replaced.
     *
     * @param channelUID channel UID
     * @param description state description for the channel
     */
    public void setDescription(ChannelUID channelUID, StateDescription description) {
        logger.trace("Adding state description for channel {}", channelUID);
        descriptions.put(channelUID, description);
    }

    /**
     * Remove all descriptions for a given thing
     *
     * @param thingUID the thing's UID
     */
    public void removeDescriptionsForThing(ThingUID thingUID) {
        logger.trace("Removing state description for thing {}", thingUID);
        descriptions.entrySet().removeIf(entry -> entry.getKey().getThingUID().equals(thingUID));
    }

    @Override
    public @Nullable StateDescription getStateDescription(Channel channel,
            @Nullable StateDescription originalStateDescription, @Nullable Locale locale) {
        if (descriptions.containsKey(channel.getUID())) {
            logger.trace("Returning new stateDescription for {}", channel.getUID());
            return descriptions.get(channel.getUID());
        } else {
            return originalStateDescription;
        }
    }
}
