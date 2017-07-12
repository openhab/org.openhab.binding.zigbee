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
import java.util.Dictionary;
import java.util.List;

import org.bubblecloud.zigbee.api.Device;
import org.bubblecloud.zigbee.api.ZigBeeApiConstants;
import org.bubblecloud.zigbee.api.ZigBeeDeviceException;
import org.bubblecloud.zigbee.api.cluster.general.Identify;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of ZigBee Identify cluster
 *
 * @see <a href=
 *      "https://people.ece.cornell.edu/land/courses/ece4760/FinalProjects/s2011/kjb79_ajm232/pmeter/ZigBee%20Cluster%20Library.pdf">
 *      ZigBee Cluster Library Specification</a>
 * @author Dovydas Girdvainis
 */

public class ZigBeeIdentifyClusterHandler extends ZigBeeClusterHandler implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeClusterHandler.class);

    private static final int DEFAULT_TIMEOUT = 60; // Measured in seconds

    private int identifyTime = DEFAULT_TIMEOUT;

    // Mandatory attributes
    private Attribute attrIdentifyTime;

    private Identify clusIdentify;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        attrIdentifyTime = coordinator.openAttribute(address, Identify.class, Attributes.IDENTIFY_TIME, this);

        clusIdentify = coordinator.openCluster(address, Identify.class);

        if (attrIdentifyTime == null || clusIdentify == null) {
            logger.error("One of the Identify cluster's mandatory attributes, for device {} , is empty", address);
            return;
        }

        initializeAttributes();

        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        if (attrIdentifyTime != null) {
            coordinator.closeAttribute(attrIdentifyTime, this);
        }
        if (clusIdentify != null) {
            coordinator.closeCluster(clusIdentify);
        }
    }

    private void initializeAttributes() {
        try {
            Integer value = (Integer) attrIdentifyTime.getValue();
            updateChannelState(new DecimalType(value));
        } catch (ZigBeeClusterException e) {
            logger.error("Error, can not get identification time for device {}", address);
            e.printStackTrace();
        }
    }

    @Override
    public String getStatus() {
        return null;
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }
        initializeAttributes();
    }

    @Override
    public void handleCommand(Command command) {
        if (initialised == false) {
            logger.error("Device {} is not initialized.", address);
            return;
        }

        if (clusIdentify == null) {
            logger.error("Identify clusster for device {} is not initialized.", address);
            return;
        }

        if (command instanceof DecimalType) {
            DecimalType value = (DecimalType) command;
            identifyTime = value.intValue();
            try {
                if (identifyTime == 0) {
                    logger.warn("Starting device {} identification for {} seconds.", address, DEFAULT_TIMEOUT);
                } else {
                    logger.warn("Starting device {} identification for {} seconds.", address, identifyTime);
                }

                clusIdentify.identify(identifyTime);

            } catch (ZigBeeDeviceException e) {
                logger.error("Sending indetification command to device {} with {} second timeout failed.", address,
                        identifyTime);
                e.printStackTrace();
            }
        }

        if (command instanceof OnOffType && command == OnOffType.ON) {
            try {
                int value = clusIdentify.IdentifyQuery();
                updateChannelState(new DecimalType(value));
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not get identification query for device {} .", address);
                e.printStackTrace();
            }
        }
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        logger.debug("ZigBee attribute reports {} from {}", reports, endPointId);

        if (attrIdentifyTime != null) {
            Object value = reports.get(attrIdentifyTime);
            if (value != null) {
                updateChannelState(new DecimalType((int) value));
            }
        }
    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, Device device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_IDENTIFY, "Identify", "Identify"));

        return channels;
    }

    @Override
    public int getClusterId() {
        return ZigBeeApiConstants.CLUSTER_ID_IDENTIFY;
    }

}