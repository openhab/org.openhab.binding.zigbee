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

import com.zsmartsystems.zigbee.CommandListener;
import com.zsmartsystems.zigbee.ZigBeeDevice;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclCommandType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 * @author Dovydas Girdvainis - Added handle refresh implementation and channel labeling from location descriptor,
 *         added a command listener to forward the direct commands to OH2
 *
 */
public class ZigBeeConverterSwitchOnoff extends ZigBeeChannelConverter
        implements ZclAttributeListener, CommandListener {

    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchOnoff.class);

    private ZclOnOffCluster clusterOnOff;
    private ZclBasicCluster basicCluster;

    private String channelLabel;
    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }
        logger.debug("{}: Initialising device on/off cluster", device.getIeeeAddress());

        clusterOnOff = (ZclOnOffCluster) device.getCluster(ZclOnOffCluster.CLUSTER_ID);

        if (clusterOnOff == null) {
            logger.error("{}: Error opening device on/off controls", device.getIeeeAddress());
            return;
        }

        // Add an attribute listener, then request the status
        clusterOnOff.addAttributeListener(this);
        clusterOnOff.getOnOff(0);

        // Add a command listener
        clusterOnOff.addCommandListenerr(this);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        try {
            clusterOnOff.setOnOffReporting(1, 600).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        logger.debug("{}: Closing device on/off cluster", device.getIeeeAddress());

        if (clusterOnOff != null) {
            clusterOnOff.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }

        boolean value = clusterOnOff.getOnOff(0);
        if (value == true) {
            updateChannelState(OnOffType.ON);
        } else {
            updateChannelState(OnOffType.OFF);
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

                OnOffType currentOnOff = null;
                if (command instanceof PercentType) {
                    if (((PercentType) command).intValue() == 0) {
                        currentOnOff = OnOffType.OFF;
                    } else {
                        currentOnOff = OnOffType.ON;
                    }
                } else if (command instanceof OnOffType) {
                    currentOnOff = (OnOffType) command;
                }

                if (clusterOnOff == null) {
                    return;
                }

                if (currentOnOff == OnOffType.ON) {
                    clusterOnOff.onCommand();
                } else {
                    clusterOnOff.offCommand();
                }
            }
        };
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeDevice device) {
        if (device.getCluster(ZclOnOffCluster.CLUSTER_ID) == null) {
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
            channelLabel = "Switch";
        }
        return createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF,
                ZigBeeBindingConstants.ITEM_TYPE_SWITCH, channelLabel);
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", device.getIeeeAddress(), attribute);
        if (attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            Boolean value = (Boolean) attribute.getLastValue();
            if (value != null && value == true) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }

    /**
     * @handlePost - changes the status of the thing, based on the command received directly from it
     * @param command - ZclCommand sent from a thing to the server
     * @return none
     *
     * @author Dovydas Girdvainis
     */
    // @Override
    public void commandReceived(final Command command) {

        ZclCommand zclCommand = (ZclCommand) command;

        if (zclCommand == null) {
            logger.debug("No command received");
            return;
        }

        if (zclCommand.getCommandId() == Integer.valueOf(ZclCommandType.ON_COMMAND.getId())) {
            updateChannelState(OnOffType.ON);
        } else {
            updateChannelState(OnOffType.OFF);
        }
    }
}
