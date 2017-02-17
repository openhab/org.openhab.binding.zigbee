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
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeOnOffClusterHandler extends ZigBeeClusterHandler implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeOnOffClusterHandler.class);

    private ZclOnOffCluster clusterOnOff;

    private boolean initialised = false;

    @Override
    public int getClusterId() {
        return ZclClusterType.ON_OFF.getId();
    }

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }
        clusterOnOff = (ZclOnOffCluster) device.getCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            logger.error("Error opening device on/off controls {}", device.getIeeeAddress());
            return;
        }

        // Add a listener, then request the status
        clusterOnOff.addAttributeListener(this);
        clusterOnOff.getOnOff(0);

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

        if (clusterOnOff != null) {
            // coordinator.closeCluster(clusterOnOff);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
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
    public List<Channel> getChannels(ThingUID thingUID, ZigBeeDevice device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF, "Switch", "Switch"));

        return channels;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, device.getIeeeAddress());
        if (attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            Boolean value = (Boolean) attribute.getLastValue();
            if (value != null && value == true) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}
