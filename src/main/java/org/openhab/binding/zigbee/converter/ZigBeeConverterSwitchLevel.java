/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
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

import com.zsmartsystems.zigbee.CommandListener;
import com.zsmartsystems.zigbee.ZigBeeDevice;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclCommandType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 * @author Dovydas Girdvainis - Added handle refresh implementation and channel labeling from location descriptor
 *         added a command listener to forward the direct commands to OH2
 *
 */
public class ZigBeeConverterSwitchLevel extends ZigBeeChannelConverter
        implements ZclAttributeListener, CommandListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchLevel.class);

    private ZclLevelControlCluster clusterLevelControl;
    private ZclBasicCluster basicCluster;

    private boolean initialised = false;

    private String channelLabel;

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

        Integer value = clusterLevelControl.getCurrentLevel(0);
        if (value != null) {
            value = value * 100 / 255;
            if (value > 100) {
                value = 100;
            }
            updateChannelState(new PercentType(value));
        }
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
    public Channel getChannel(ThingUID thingUID, ZigBeeDevice device) {
        if (device.getCluster(ZclLevelControlCluster.CLUSTER_ID) == null) {
            return null;
        }
        basicCluster = (ZclBasicCluster) device.getCluster(ZclBasicCluster.CLUSTER_ID);
        if (basicCluster == null) {
            logger.error("{}: Error opening device baisic controls", device.getIeeeAddress());
        }

        // Get cluster location descriptor for channel label
        if (basicCluster != null) {
            channelLabel = basicCluster.getLocationDescription(0);
        } else {
            channelLabel = "Dimmer";
        }
        return createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL,
                ZigBeeBindingConstants.ITEM_TYPE_DIMMER, channelLabel);
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

    /**
     * commandReceived - changes the status of the thing, based on the command received directly from it
     *
     * @param command - ZclCommand sent from a thing to the server
     * @return none
     *
     * @author Dovydas Girdvainis
     */
    @Override
    public void commandReceived(final com.zsmartsystems.zigbee.Command command) {

        ZclCommand zclCommand = (ZclCommand) command;

        if (zclCommand == null) {
            logger.debug("No command received");
            return;
        }

        if (zclCommand.getCommandId() == Integer.valueOf(ZclCommandType.MOVE_TO_LEVEL_COMMAND.getId())) {
            updateChannelState(new PercentType(((MoveToLevelCommand) zclCommand).getLevel()));
        } else {
            logger.debug("Unhandeled command: {}", zclCommand.toString());
            return;
        }
    }

}
