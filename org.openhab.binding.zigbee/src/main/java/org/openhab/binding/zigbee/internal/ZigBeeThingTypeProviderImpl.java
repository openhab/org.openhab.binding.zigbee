/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.openhab.binding.zigbee.thingtype.ZigBeeThingTypeProvider;
import org.osgi.service.component.annotations.Component;

/**
 * This class is the basic binding implementation to provide non-generic thing types.
 *
 * @author Thomas Höfer - Initial Implementation
 */
@Component(immediate = true)
public final class ZigBeeThingTypeProviderImpl implements ZigBeeThingTypeProvider {

    @Override
    public Set<@NonNull ThingTypeUID> getThingTypeUIDs() {
        return ZigBeeThingTypeMatcher.getInstance().getSupportedThingTypeUIDs();
    }
}
