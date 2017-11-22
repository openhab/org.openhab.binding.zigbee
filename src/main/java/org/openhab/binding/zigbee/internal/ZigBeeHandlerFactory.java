/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorCC2531Handler;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorEmberHandler;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorTelegesisHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZigBeeHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeHandlerFactory.class);

    private TranslationProvider translationProvider;

    protected void setTranslationProvider(TranslationProvider i18nProvider) {
        translationProvider = i18nProvider;
    }

    protected void removeTranslationProvider(TranslationProvider i18nProvider) {
        translationProvider = null;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return ZigBeeBindingConstants.BINDING_ID.equals(thingTypeUID.getBindingId());
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        logger.debug("Creating coordinator handler for {}", thing);

        // Handle coordinators here
        if (thingTypeUID.equals(ZigBeeBindingConstants.COORDINATOR_TYPE_EMBER)) {
            return new ZigBeeCoordinatorEmberHandler((Bridge) thing, translationProvider);
        }
        if (thingTypeUID.equals(ZigBeeBindingConstants.COORDINATOR_TYPE_CC2531)) {
            return new ZigBeeCoordinatorCC2531Handler((Bridge) thing, translationProvider);
        }
        if (thingTypeUID.equals(ZigBeeBindingConstants.COORDINATOR_TYPE_TELEGESIS)) {
            return new ZigBeeCoordinatorTelegesisHandler((Bridge) thing, translationProvider);
        }

        // Everything else gets handled in a single handler
        return new ZigBeeThingHandler(thing, translationProvider);
    }
}
