/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
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

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterColorColor extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
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

        clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.error("Error opening device control controls {}", endpoint.getIeeeAddress());
            return;
        }

        clusterLevelControl = (ZclLevelControlCluster) endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.error("Error opening device level controls {}", endpoint.getIeeeAddress());
            return;
        }

        clusterColorControl.bind();
        clusterLevelControl.bind();

        // Add a listener, then request the status
        clusterColorControl.addAttributeListener(this);
        clusterLevelControl.addAttributeListener(this);

        clusterColorControl.getCurrentHue(0);
        clusterColorControl.getCurrentSaturation(0);
        clusterLevelControl.getCurrentLevel(0);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        try {
            clusterColorControl.setCurrentHueReporting(1, 600, 1).get();
            clusterColorControl.setCurrentSaturationReporting(1, 600, 1).get();
            clusterLevelControl.setCurrentLevelReporting(1, 600, 1).get();
        } catch (ExecutionException | InterruptedException e) {
            logger.debug("Exception configuring color reporting", e);
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
        clusterColorControl.getCurrentHue(0);
        clusterColorControl.getCurrentSaturation(0);
        clusterLevelControl.getCurrentLevel(0);
    }

    @Override
    public Runnable handleCommand(final Command command) {
        return new Runnable() {
            @Override
            public void run() {
                if (initialised == false) {
                    return;
                }

                int level;
                try {
                    if (command instanceof HSBType) {
                        HSBType hsb = (HSBType) command;
                        clusterColorControl
                                .moveToHueAndSaturationCommand((int) (hsb.getHue().doubleValue() * 254.0 / 360.0 + 0.5),
                                        (int) (hsb.getSaturation().doubleValue() * 254.0 / 100.0 + 0.5), 10)
                                .get();
                        level = hsb.getBrightness().intValue();
                    } else if (command instanceof PercentType) {
                        level = ((PercentType) command).intValue();
                    } else if (command instanceof OnOffType) {
                        level = (OnOffType) command == OnOffType.ON ? 100 : 0;
                    } else {
                        return;
                    }

                    clusterLevelControl.moveToLevelWithOnOffCommand((int) (level * 254.0 / 100.0 + 0.5), 10).get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID) == null
                || endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID) == null) {
            return null;
        }
        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_COLOR_COLOR,
                ZigBeeBindingConstants.ITEM_TYPE_COLOR, "Color");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, endpoint.getIeeeAddress());
        if (attribute.getCluster() == ZclClusterType.COLOR_CONTROL) {
            if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTHUE) {
                Integer value = (Integer) attribute.getLastValue();
                if (value != null) {
                    DecimalType hue = new DecimalType(value);
                    currentHSB = new HSBType(hue, currentHSB.getSaturation(), currentHSB.getBrightness());
                    updateChannelState(currentHSB);
                }
            }
            if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTSATURATION) {
                Integer value = (Integer) attribute.getLastValue();
                if (value != null) {
                    PercentType saturation = new PercentType(value * 100 / 254);
                    currentHSB = new HSBType(currentHSB.getHue(), saturation, currentHSB.getBrightness());
                    updateChannelState(currentHSB);
                }
            }
        } else if (attribute.getCluster() == ZclClusterType.LEVEL_CONTROL
                && attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                PercentType brightness = new PercentType(value * 100 / 254);
                currentHSB = new HSBType(currentHSB.getHue(), currentHSB.getSaturation(), brightness);
                updateChannelState(currentHSB);
            }
        }
    }

}
