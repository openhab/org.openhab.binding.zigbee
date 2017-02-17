/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

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
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeColorClusterHandler extends ZigBeeClusterHandler implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeColorClusterHandler.class);

    private HSBType currentHSB;
    private ZclColorControlCluster clusterColorControl;
    private ZclLevelControlCluster clusterLevelControl;

    private boolean initialised = false;

    @Override
    public int getClusterId() {
        return ZclClusterType.COLOR_CONTROL.getId();
    }

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
                    clusterColorControl.moveToHueCommand((int) (hue * 254.0 / 360.0 + 0.5), 0, 10).get();
                    clusterColorControl.moveToSaturationCommand((int) (saturation * 254.0 / 100.0 + 0.5), 10).get();
                    clusterLevelControl.moveToLevelWithOnOffCommand(level, 10).get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }

                /*
                 * // Color Temperature
                 * PercentType colorTemp = PercentType.ZERO;
                 * if (command instanceof PercentType) {
                 * colorTemp = (PercentType) command;
                 * } else if (command instanceof OnOffType) {
                 * if ((OnOffType) command == OnOffType.ON) {
                 * colorTemp = PercentType.HUNDRED;
                 * } else {
                 * colorTemp = PercentType.ZERO;
                 * }
                 * }
                 *
                 * // Range of 2000K to 6500K, gain = 4500K, offset = 2000K
                 * double kelvin = colorTemp.intValue() * 4500.0 / 100.0 + 2000.0;
                 * try {
                 * clusColor.moveToColorTemperature((short) (1e6 / kelvin + 0.5), 10);
                 * } catch (ZigBeeDeviceException e) {
                 * e.printStackTrace();
                 * }
                 */
            }
        };
    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, ZigBeeDevice device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_COLOR_COLOR, "Color", "Color"));
        // channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_COLOR_TEMPERATURE, "Dimmer",
        // "Color Temperature"));

        return channels;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, device.getIeeeAddress());
        if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTHUE) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                // currentHSB = new HSBType(value, currentHSB.getSaturation(), currentHSB.getBrightness());
                // value = value * 100 / 255;
                // if (value > 100) {
                // value = 100;
                // }
                // updateChannelState(new PercentType(value));
            }
        }
    }

}
