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
package org.openhab.binding.zigbee.cc2531.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.binding.zigbee.cc2531.CC2531BindingConstants;
import org.openhab.binding.zigbee.cc2531.handler.CC2531Handler;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link CC2531HandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Chris Jackson - Initial contribution
 */
@Component(service = ThingHandlerFactory.class, configurationPid = "org.openhab.binding.zigbee.cc2531")
@NonNullByDefault
public class CC2531HandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections
            .singleton(CC2531BindingConstants.THING_TYPE_CC2531);

    private final Map<ThingUID, ServiceRegistration<?>> coordinatorHandlerRegs = new HashMap<>();

    private final SerialPortManager serialPortManager;

    @Nullable
    private ZigBeeChannelConverterFactory zigbeeChannelConverterFactory;

    @Activate
    public CC2531HandlerFactory(final @Reference SerialPortManager serialPortManager) {
        this.serialPortManager = serialPortManager;
    }

    @Reference
    protected void setZigBeeChannelConverterFactory(ZigBeeChannelConverterFactory zigbeeChannelConverterFactory) {
        this.zigbeeChannelConverterFactory = zigbeeChannelConverterFactory;
    }

    protected void unsetZigBeeChannelConverterFactory(ZigBeeChannelConverterFactory zigbeeChannelConverterFactory) {
        this.zigbeeChannelConverterFactory = null;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        ZigBeeCoordinatorHandler coordinator = null;
        if (thingTypeUID.equals(CC2531BindingConstants.THING_TYPE_CC2531)) {
            coordinator = new CC2531Handler((Bridge) thing, serialPortManager, zigbeeChannelConverterFactory);
        }

        if (coordinator != null) {
            coordinatorHandlerRegs.put(coordinator.getThing().getUID(), bundleContext
                    .registerService(ZigBeeCoordinatorHandler.class.getName(), coordinator, new Hashtable<>()));

            return coordinator;
        }

        return null;
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof CC2531Handler) {
            ServiceRegistration<?> coordinatorHandlerReg = coordinatorHandlerRegs.get(thingHandler.getThing().getUID());
            if (coordinatorHandlerReg != null) {
                coordinatorHandlerReg.unregister();
                coordinatorHandlerRegs.remove(thingHandler.getThing().getUID());
            }
        }
    }
}
