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
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterSwitchOnoff extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchOnoff.class);

    private ZclOnOffCluster clusterOnOff;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }
        logger.debug("{}: Initialising device on/off cluster", endpoint.getIeeeAddress());

        clusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            logger.error("{}: Error opening device on/off controls", endpoint.getIeeeAddress());
            return;
        }

        clusterOnOff.bind();

        // Add a listener, then request the status
        clusterOnOff.addAttributeListener(this);
        clusterOnOff.getOnOff(0);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        clusterOnOff.setOnOffReporting(1, 600);
        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        logger.debug("{}: Closing device on/off cluster", endpoint.getIeeeAddress());

        if (clusterOnOff != null) {
            clusterOnOff.removeAttributeListener(this);
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
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID) == null) {
            return null;
        }
        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF,
                ZigBeeBindingConstants.ITEM_TYPE_SWITCH, "Switch");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.ON_OFF && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            Boolean value = (Boolean) attribute.getLastValue();
            if (value != null && value == true) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}
