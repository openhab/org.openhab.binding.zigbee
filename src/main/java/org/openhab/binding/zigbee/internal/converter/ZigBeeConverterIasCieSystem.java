/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;

/**
 * Converter for the IAS Standard CIE System sensor.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterIasCieSystem extends ZigBeeConverterIas implements ZclCommandListener {
    @Override
    public boolean initializeConverter() {
        return super.initializeConverter();
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (!supportsIasChannel(endpoint, ZoneTypeEnum.STANDARD_CIE)) {
            return null;
        }

        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_IAS_STANDARDCIE_SYSTEM,
                ZigBeeBindingConstants.ITEM_TYPE_SWITCH, "CIE Standard System Alarm");
    }
}
