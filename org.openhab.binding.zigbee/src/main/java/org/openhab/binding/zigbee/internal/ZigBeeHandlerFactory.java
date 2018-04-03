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

import org.eclipse.smarthome.config.core.ConfigDescriptionProvider;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.eclipse.smarthome.core.thing.type.ThingTypeRegistry;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

/**
 * The {@link ZigBeeHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Chris Jackson - Initial contribution
 * @author Kai Kreuzer - Refactored to use DS annotations
 */
@Component(immediate = true, service = { ThingHandlerFactory.class })
public class ZigBeeHandlerFactory extends BaseThingHandlerFactory {

    private final ZigBeeThingTypeMatcher thingTypeMatcher = new ZigBeeThingTypeMatcher();

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setConfigDescriptionRegistry(ThingTypeRegistry thingTypeRegistry) {
        thingTypeMatcher.setThingTypeRegistry(thingTypeRegistry);
    }

    protected void unsetConfigDescriptionRegistry(ThingTypeRegistry thingTypeRegistry) {
        thingTypeMatcher.setThingTypeRegistry(thingTypeRegistry);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return ZigBeeBindingConstants.SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        if (!ZigBeeBindingConstants.SUPPORTED_THING_TYPES.contains(thing.getThingTypeUID())) {
            return null;
        }

        ZigBeeThingHandler handler = new ZigBeeThingHandler(thing, thingTypeMatcher);

        bundleContext.registerService(ConfigDescriptionProvider.class.getName(), handler,
                new Hashtable<String, Object>());

        return handler;
    }
}