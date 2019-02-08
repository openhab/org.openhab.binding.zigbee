/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.thingtype;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;

/**
 * The {@link ZigBeeThingTypeProvider} can be registered as OSGi service in order to provide additional ZigBee
 * {@link ThingTypeUID}s that are to be supported by the {@link ThingHandlerFactory} of this binding. These thing types
 * are <b>not</b> tracked by the factories that are responsible to create the {@link ZigBeeCoordinatorHandler}s.
 *
 * @author Thomas Höfer - Initial contribution
 */
@NonNullByDefault
public interface ZigBeeThingTypeProvider {

    /**
     * Provides the set of ZigBee thing types that are supported by this thing type provider.
     *
     * @return the set of ZigBee thing types supported by this thing type provider.
     */
    Set<ThingTypeUID> getThingTypeUIDs();
}
