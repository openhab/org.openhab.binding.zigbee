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
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeLevelClusterHandler extends ZigBeeClusterHandler implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeLevelClusterHandler.class);

    private ZclLevelControlCluster clusterLevelControl;

    private boolean initialised = false;

    @Override
    public int getClusterId() {
        return ZclClusterType.LEVEL_CONTROL.getId();
    }

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        clusterLevelControl = (ZclLevelControlCluster) device.getCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.error("Error opening device level controls {}", device.getIeeeAddress());
            return;
        }

        // Add a listener, then request the status
        clusterLevelControl.addAttributeListener(this);
        clusterLevelControl.getCurrentLevel(0);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        clusterLevelControl.setCurrentLevelReporting(1, 600, 1);
        initialised = true;
    }

    @Override
    public void disposeConverter() {
        clusterLevelControl.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        clusterLevelControl.getCurrentLevelAsync();

    }

    @Override
    public Runnable handleCommand(final Command command) {
        return new Runnable() {
            @Override
            public void run() {
                if (initialised == false) {
                    return;
                }

                int level = 0;
                if (command instanceof PercentType) {
                    level = ((PercentType) command).intValue();
                } else if (command instanceof OnOffType) {
                    if ((OnOffType) command == OnOffType.ON) {
                        level = 100;
                    } else {
                        level = 0;
                    }
                }

                clusterLevelControl.moveToLevelWithOnOffCommand((int) (level * 254.0 / 100.0 + 0.5), 10);
            }
        };
    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, ZigBeeDevice device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SWITCH_DIMMER, "Dimmer", "Dimmer"));

        return channels;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, device.getIeeeAddress());
        if (attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                value = value * 100 / 255;
                if (value > 100) {
                    value = 100;
                }
                updateChannelState(new PercentType(value));
            }
        }
    }

}
