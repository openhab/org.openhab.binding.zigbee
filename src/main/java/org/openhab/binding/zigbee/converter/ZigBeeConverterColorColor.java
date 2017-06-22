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

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeDevice;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterColorColor extends ZigBeeChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterColorColor.class);

    private HSBType currentHSB;
    private ZclColorControlCluster clusterColorControl;
    private ZclLevelControlCluster clusterLevelControl;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        currentHSB = new HSBType();

        clusterColorControl = (ZclColorControlCluster) device.getCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.error("Error opening device control controls {}", device.getIeeeAddress());
            return;
        }

        clusterLevelControl = (ZclLevelControlCluster) device.getCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.error("Error opening device level controls {}", device.getIeeeAddress());
            return;
        }

        // Add a listener, then request the status
        clusterLevelControl.addAttributeListener(this);
        clusterColorControl.addAttributeListener(this);

        clusterColorControl.getCurrentHue(0);
        clusterLevelControl.getCurrentLevel(0);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        try {
            clusterColorControl.setCurrentHueReporting(1, 600, 1).get();
            clusterLevelControl.setCurrentLevelReporting(1, 600, 1).get();
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
        clusterLevelControl.removeAttributeListener(this);
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

                if (command instanceof HSBType) {
                    currentHSB = new HSBType(command.toString());
                } else if (command instanceof PercentType) {
                    currentHSB = new HSBType(currentHSB.getHue(), (PercentType) command, PercentType.HUNDRED);
                } else if (command instanceof OnOffType) {
                    PercentType saturation;
                    if ((OnOffType) command == OnOffType.ON) {
                        saturation = PercentType.HUNDRED;
                    } else {
                        saturation = PercentType.ZERO;
                    }
                    currentHSB = new HSBType(currentHSB.getHue(), saturation, PercentType.HUNDRED);
                }

                int hue = currentHSB.getHue().intValue();
                int saturation = currentHSB.getSaturation().intValue();
                int level = (int) (currentHSB.getBrightness().intValue() * 254.0 / 100.0 + 0.5);
                try {
                    logger.debug("COLOR 1");
                    clusterColorControl.moveToHueAndSaturationCommand((int) (hue * 254.0 / 360.0 + 0.5),
                            (int) (saturation * 254.0 / 100.0 + 0.5), 10).get();
                    logger.debug("COLOR 2");
                    clusterColorControl.moveToSaturationCommand((int) (saturation * 254.0 / 100.0 + 0.5), 10).get();
                    logger.debug("COLOR 3");
                    clusterLevelControl.moveToLevelWithOnOffCommand(level, 10).get();
                    logger.debug("COLOR 4");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeDevice device) {
        if (device.getCluster(ZclColorControlCluster.CLUSTER_ID) == null
                || device.getCluster(ZclLevelControlCluster.CLUSTER_ID) == null) {
            return null;
        }
        return createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_COLOR_COLOR,
                ZigBeeBindingConstants.ITEM_TYPE_COLOR, "Color");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, device.getIeeeAddress());
        if (attribute.getId() != ZclColorControlCluster.ATTR_CURRENTHUE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                DecimalType decimalValue = new DecimalType(value);
                currentHSB = new HSBType(decimalValue, currentHSB.getSaturation(), currentHSB.getBrightness());
                updateChannelState(currentHSB);
            }
        }
    }

}
