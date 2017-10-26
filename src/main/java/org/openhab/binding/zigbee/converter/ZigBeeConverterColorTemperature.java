/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterColorTemperature extends ZigBeeChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterColorTemperature.class);

    private ZclColorControlCluster clusterColorControl;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        clusterColorControl = (ZclColorControlCluster) device.getCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.error("Error opening device control controls {}", device.getIeeeAddress());
            return;
        }

        clusterColorControl.addAttributeListener(this);

        clusterColorControl.getColorTemperature(0);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        try {
            clusterColorControl.setCurrentHueReporting(1, 600, 1).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        initialised = true;
    }

    @Override
    public void disposeConverter() {
        clusterColorControl.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {

    }

    @Override
    public Runnable handleCommand(final Command command) {
        return new Runnable() {
            @Override
            public void run() {
                if (initialised == false) {
                    return;
                }

                // Color Temperature
                PercentType colorTemp = PercentType.ZERO;
                if (command instanceof PercentType) {
                    colorTemp = (PercentType) command;
                } else if (command instanceof OnOffType) {
                    if ((OnOffType) command == OnOffType.ON) {
                        colorTemp = PercentType.HUNDRED;
                    } else {
                        colorTemp = PercentType.ZERO;
                    }
                }

                // Range of 2000K to 6500K, gain = 4500K, offset = 2000K
                double kelvin = colorTemp.intValue() * 4500.0 / 100.0 + 2000.0;
                clusterColorControl.moveToColorTemperatureCommand((int) (1e6 / kelvin + 0.5), 10);
            }
        };
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint device) {
        if (device.getCluster(ZclColorControlCluster.CLUSTER_ID) == null) {
            return null;
        }
        return createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_COLOR_TEMPERATURE,
                ZigBeeBindingConstants.ITEM_TYPE_DIMMER, "Color Temperature");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, device.getIeeeAddress());
        if (attribute.getId() == ZclColorControlCluster.ATTR_COLORTEMPERATURE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
            }
        }
    }

}
