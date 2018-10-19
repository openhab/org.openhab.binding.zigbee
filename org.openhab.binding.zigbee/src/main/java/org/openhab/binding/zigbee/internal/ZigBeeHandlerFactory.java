/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import java.util.Hashtable;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.config.core.ConfigDescriptionProvider;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link ZigBeeHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Chris Jackson - Initial contribution
 * @author Kai Kreuzer - Refactored to use DS annotations
 */
@Component(immediate = true, service = { ThingHandlerFactory.class })
public class ZigBeeHandlerFactory extends BaseThingHandlerFactory {
    private final ZigBeeThingTypeMatcher matcher = new ZigBeeThingTypeMatcher();

    @NonNullByDefault({})
    private ZigBeeDynamicStateDescriptionProvider dynamicStateDescriptionProvider;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        // The core binding provides dynamic device creation
        if (thingTypeUID.equals(ZigBeeBindingConstants.THING_TYPE_GENERIC_DEVICE)) {
            return true;
        }

        return matcher.getSupportedThingTypeUIDs().contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        if (!supportsThingType(thing.getThingTypeUID())) {
            return null;
        }

        ZigBeeThingHandler handler = new ZigBeeThingHandler(thing, dynamicStateDescriptionProvider);
        bundleContext.registerService(ConfigDescriptionProvider.class.getName(), handler,
                new Hashtable<String, Object>());

        return handler;
    }

    @Reference
    protected void setDynamicStateDescriptionProvider(ZigBeeDynamicStateDescriptionProvider provider) {
        dynamicStateDescriptionProvider = provider;
    }

    protected void unsetDynamicStateDescriptionProvider(ZigBeeDynamicStateDescriptionProvider provider) {
        dynamicStateDescriptionProvider = null;
    }
}