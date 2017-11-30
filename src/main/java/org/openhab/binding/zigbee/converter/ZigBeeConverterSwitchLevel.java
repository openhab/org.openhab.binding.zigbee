/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

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
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterSwitchLevel extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchLevel.class);

    private ZclLevelControlCluster clusterLevelControl;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        clusterLevelControl = (ZclLevelControlCluster) endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.error("Error opening device level controls {}", endpoint.getIeeeAddress());
            return;
        }

        clusterLevelControl.bind();

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
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID) == null) {
            return null;
        }
        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL,
                ZigBeeBindingConstants.ITEM_TYPE_DIMMER, "Dimmer");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, endpoint.getIeeeAddress());
        if (attribute.getCluster() == ZclClusterType.LEVEL_CONTROL
                && attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null) {
                value = value * 100 / 255;
                updateChannelState(new PercentType(value));
            }
        }
    }

}
