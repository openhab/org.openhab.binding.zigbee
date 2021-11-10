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
package org.openhab.binding.zigbee.tuya.internal;

import java.util.Hashtable;

import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.discovery.ZigBeeThingTypeMatcher;
import org.openhab.binding.zigbee.handler.ZigBeeBaseThingHandler;
import org.openhab.binding.zigbee.handler.ZigBeeGenericThingHandler;
import org.openhab.binding.zigbee.handler.ZigBeeIsAliveTracker;
import org.openhab.binding.zigbee.tuya.TuyaBindingConstants;
import org.openhab.binding.zigbee.tuya.handler.TuyaBlindsThingHandler;
import org.openhab.core.config.core.ConfigDescriptionProvider;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.openhab.core.thing.type.DynamicStateDescriptionProvider;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link TuyaHandlerFactory} is responsible for creating things and thing
 * handlers for Tuya devices.
 *
 * @author Chris Jackson - Initial contribution
 */
@Component(service = ThingHandlerFactory.class)
public class TuyaHandlerFactory extends BaseThingHandlerFactory {

    private final ZigBeeThingTypeMatcher matcher = new ZigBeeThingTypeMatcher();

    private ZigBeeChannelConverterFactory zigbeeChannelConverterFactory;
    private ZigBeeIsAliveTracker zigbeeIsAliveTracker;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return matcher.getSupportedThingTypeUIDs().contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        if (!supportsThingType(thing.getThingTypeUID())) {
            return null;
        }

        ZigBeeBaseThingHandler handler;

        // Check for thing types with a custom thing handler
        if (thing.getThingTypeUID().equals(TuyaBindingConstants.THING_TYPE_TUYA_BLIND_AM25)) {
            handler = new TuyaBlindsThingHandler(thing, zigbeeChannelConverterFactory, zigbeeIsAliveTracker);
        } else {
            handler = new ZigBeeGenericThingHandler(thing, zigbeeChannelConverterFactory, zigbeeIsAliveTracker);
        }

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

    @Reference
    protected void setZigbeeIsAliveTracker(ZigBeeIsAliveTracker zigbeeIsAliveTracker) {
        this.zigbeeIsAliveTracker = zigbeeIsAliveTracker;
    }

    protected void unsetZigbeeIsAliveTracker(ZigBeeIsAliveTracker zigbeeIsAliveTracker) {
        this.zigbeeIsAliveTracker = null;
    }
}
