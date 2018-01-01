/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
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

import com.zsmartsystems.zigbee.CommandResult;
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

    @Override
    public boolean initializeConverter() {
        logger.debug("{}: Initialising device on/off cluster", endpoint.getIeeeAddress());

        clusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            logger.error("{}: Error opening device on/off controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = clusterOnOff.bind().get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 10 minutes.
                CommandResult reportingResponse = clusterOnOff.setOnOffReporting(1, 600).get();
                if (reportingResponse.isError()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }

        // Add a listener, then request the status
        clusterOnOff.addAttributeListener(this);
        clusterOnOff.getOnOff(0);

        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device on/off cluster", endpoint.getIeeeAddress());

        clusterOnOff.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        clusterOnOff.getOnOff(0);
    }

    @Override
    public void handleCommand(final Command command) {
        OnOffType cmdOnOff = null;
        if (command instanceof PercentType) {
            if (((PercentType) command).intValue() == 0) {
                cmdOnOff = OnOffType.OFF;
            } else {
                cmdOnOff = OnOffType.ON;
            }
        } else if (command instanceof OnOffType) {
            cmdOnOff = (OnOffType) command;
        }

        if (cmdOnOff == OnOffType.ON) {
            clusterOnOff.onCommand();
        } else {
            clusterOnOff.offCommand();
        }
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
