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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.smarthome.config.core.ConfigDescriptionProvider;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.eclipse.smarthome.core.thing.type.DynamicStateDescriptionProvider;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.thingtype.ZigBeeThingTypeProvider;
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
 * @author Thomas Höfer - Injected zigbeeChannelConverterFactory via DS
 */
@Component(immediate = true, service = { ThingHandlerFactory.class })
public class ZigBeeHandlerFactory extends BaseThingHandlerFactory {

    private ZigBeeChannelConverterFactory zigbeeChannelConverterFactory;

    private final Set<ThingTypeUID> thingTypeUIDs = new CopyOnWriteArraySet<>();

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        // The core binding provides dynamic device creation
        if (thingTypeUID.equals(ZigBeeBindingConstants.THING_TYPE_GENERIC_DEVICE)) {
            return true;
        }

        return thingTypeUIDs.contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        if (!supportsThingType(thing.getThingTypeUID())) {
            return null;
        }

        ZigBeeThingHandler handler = new ZigBeeThingHandler(thing, zigbeeChannelConverterFactory);
        bundleContext.registerService(ConfigDescriptionProvider.class.getName(), handler,
                new Hashtable<String, Object>());
        bundleContext.registerService(DynamicStateDescriptionProvider.class.getName(), handler,
                new Hashtable<String, Object>());

        return handler;
    }

    @Reference
    protected void setZigBeeChannelConverterFactory(ZigBeeChannelConverterFactory zigbeeChannelConverterFactory) {
        this.zigbeeChannelConverterFactory = zigbeeChannelConverterFactory;
    }

    protected void unsetZigBeeChannelConverterFactory(ZigBeeChannelConverterFactory zigbeeChannelConverterFactory) {
        this.zigbeeChannelConverterFactory = null;
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void addZigBeeThingTypeProvider(ZigBeeThingTypeProvider zigBeeThingTypeProvider) {
        thingTypeUIDs.addAll(zigBeeThingTypeProvider.getThingTypeUIDs());
    }

    public void removeZigBeeThingTypeProvider(ZigBeeThingTypeProvider zigBeeThingTypeProvider) {
        thingTypeUIDs.removeAll(zigBeeThingTypeProvider.getThingTypeUIDs());
    }
}