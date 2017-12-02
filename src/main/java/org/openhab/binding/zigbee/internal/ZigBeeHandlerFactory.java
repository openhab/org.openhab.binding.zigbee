/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.i18n.TranslationProvider;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.discovery.ZigBeeDiscoveryService;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorCC2531Handler;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorEmberHandler;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorTelegesisHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZigBeeHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Chris Jackson - Initial contribution
 * @author Kai Kreuzer - Refactored to use DS annotations
 */
@Component(immediate = true, service = { ThingHandlerFactory.class })
public class ZigBeeHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeHandlerFactory.class);

    private Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    private TranslationProvider translationProvider;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    protected void setTranslationProvider(TranslationProvider i18nProvider) {
        translationProvider = i18nProvider;
    }

    protected void unsetTranslationProvider(TranslationProvider i18nProvider) {
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

        ZigBeeCoordinatorHandler coordinator = null;

        // Handle coordinators here
        if (thingTypeUID.equals(ZigBeeBindingConstants.COORDINATOR_TYPE_CC2531)) {
            coordinator = new ZigBeeCoordinatorCC2531Handler((Bridge) thing, translationProvider);
        }
        // if (thingTypeUID.equals(ZigBeeBindingConstants.COORDINATOR_TYPE_CONBEE)) {
        // coordinator = new ZigBeeCoordinatorConBeeHandler((Bridge) thing, translationProvider);
        // }
        if (thingTypeUID.equals(ZigBeeBindingConstants.COORDINATOR_TYPE_EMBER)) {
            coordinator = new ZigBeeCoordinatorEmberHandler((Bridge) thing, translationProvider);
        }
        if (thingTypeUID.equals(ZigBeeBindingConstants.COORDINATOR_TYPE_TELEGESIS)) {
            coordinator = new ZigBeeCoordinatorTelegesisHandler((Bridge) thing, translationProvider);
        }

        if (coordinator != null) {
            ZigBeeDiscoveryService discoveryService = new ZigBeeDiscoveryService(coordinator);
            discoveryService.activate();

            discoveryServiceRegs.put(coordinator.getThing().getUID(), bundleContext.registerService(
                    DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));

            return coordinator;
        }

        // Everything else gets handled in a single handler
        return new ZigBeeThingHandler(thing, translationProvider);
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof ZigBeeCoordinatorHandler) {
            ServiceRegistration<?> serviceReg = this.discoveryServiceRegs.get(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                // remove discovery service, if bridge handler is removed
                ZigBeeDiscoveryService service = (ZigBeeDiscoveryService) bundleContext
                        .getService(serviceReg.getReference());
                if (service != null) {
                    service.deactivate();
                }
                serviceReg.unregister();
                discoveryServiceRegs.remove(thingHandler.getThing().getUID());
            }
        }
    }
}
