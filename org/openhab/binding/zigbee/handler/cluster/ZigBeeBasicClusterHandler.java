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
import org.bubblecloud.zigbee.api.cluster.general.Basic;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of ZigBee Basic cluster
 *
 * @see <a href=
 *      "https://people.ece.cornell.edu/land/courses/ece4760/FinalProjects/s2011/kjb79_ajm232/pmeter/ZigBee%20Cluster%20Library.pdf">
 *      ZigBee Cluster Library Specification</a>
 * @author Dovydas Girdvainis
 */

public class ZigBeeBasicClusterHandler extends ZigBeeClusterHandler implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeClusterHandler.class);

    // Device information attributes
    // Mandatory attributes
    // private Attribute attrZclVersion;
    // private Attribute attrPwrSource; // Will it give hex values or 1 of 8 states?

    // Optional attributes
    // private Attribute attrAppVer;
    // private Attribute attrStackVer;
    // private Attribute attrHwVer;
    // private Attribute attrManufName;
    // private Attribute attrModelId;
    // private Attribute attrDateCode;

    // Device settings attributes
    // Optional attributes
    // private Attribute attrLocDesc;
    // private Attribute attrPhysEnv;
    private Attribute attrDeviceEnabled;
    // private Attribute attrAlarmMask;

    private Basic clusBasic;

    private boolean initialised = false;

    @Override
    public int getClusterId() {
        return ZigBeeApiConstants.CLUSTER_ID_BASIC;
    }

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        /*
         * attrZclVersion = coordinator.openAttribute(address, Basic.class, Attributes.ZCL_VERSION, this);
         * attrAppVer = coordinator.openAttribute(address, Basic.class, Attributes.APPLICATION_VERSION, this);
         * attrStackVer = coordinator.openAttribute(address, Basic.class, Attributes.STACK_VERSION, this);
         * attrHwVer = coordinator.openAttribute(address, Basic.class, Attributes.HW_VERSION, this);
         * attrManufName = coordinator.openAttribute(address, Basic.class, Attributes.MANUFACTURER_NAME, this);
         * attrModelId = coordinator.openAttribute(address, Basic.class, Attributes.MODEL_IDENTIFIER, this);
         * attrDateCode = coordinator.openAttribute(address, Basic.class, Attributes.DATE_CODE, this);
         * attrPwrSource = coordinator.openAttribute(address, Basic.class, Attributes.POWER_SOURCE, this);
         * attrLocDesc = coordinator.openAttribute(address, Basic.class, Attributes.LOCATION_DESCRIPTION, this);
         * attrPhysEnv = coordinator.openAttribute(address, Basic.class, Attributes.PHYSICAL_ENVIRONMENT, this);
         */
        attrDeviceEnabled = coordinator.openAttribute(address, Basic.class, Attributes.DEVICE_ENABLED, this);
        // attrAlarmMask = coordinator.openAttribute(address, Basic.class, Attributes.ALARM_MASK, this);

        clusBasic = coordinator.openCluster(address, Basic.class);

        if (attrDeviceEnabled == null || clusBasic == null) {
            logger.error("One of the basic cluster's mandatory attributes, for device {} , is empty", address);
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

        /*
         * if (attrZclVersion != null) {
         * coordinator.closeAttribute(attrZclVersion, this);
         * }
         * if (attrAppVer != null) {
         * coordinator.closeAttribute(attrAppVer, this);
         * }
         * if (attrStackVer != null) {
         * coordinator.closeAttribute(attrStackVer, this);
         * }
         * if (attrHwVer != null) {
         * coordinator.closeAttribute(attrHwVer, this);
         * }
         * if (attrManufName != null) {
         * coordinator.closeAttribute(attrManufName, this);
         * }
         * if (attrModelId != null) {
         * coordinator.closeAttribute(attrModelId, this);
         * }
         * if (attrPwrSource != null) {
         * coordinator.closeAttribute(attrPwrSource, this);
         * }
         * if (clusBasic != null) {
         * coordinator.closeCluster(clusBasic);
         * }
         */
    }

    @Override
    public String getStatus() {
        return null;
    }

    private void initializeAttributes() {
        try {
            /*
             * Integer zclVer = (Integer) attrZclVersion.getValue();
             * Integer appVer = (Integer) attrAppVer.getValue();
             * Integer hwVer = (Integer) attrHwVer.getValue();
             * Integer stackVer = (Integer) attrStackVer.getValue();
             * Integer pwrSource = (Integer) attrPwrSource.getValue();
             * String manufName = (String) attrManufName.getValue();
             * String modelID = (String) attrModelId.getValue();
             * String dateCode = (String) attrDateCode.getValue();
             * String locDesc = (String) attrLocDesc.getValue();
             * Integer physEnv = (Integer) attrPhysEnv.getValue();
             */
            Object deviceEnabled = attrDeviceEnabled.getValue();
            // String alarmMask = (String) attrAlarmMask.getValue(); // Should I cast it to string, or is there a better
            // way?

            // Should be properties
            /*
             * updateChannelState(new DecimalType(zclVer));
             * updateChannelState(new DecimalType(appVer));
             * updateChannelState(new DecimalType(hwVer));
             * updateChannelState(new DecimalType(stackVer));
             * updateChannelState(new DecimalType(pwrSource));
             * updateChannelState(new StringType(manufName));
             * updateChannelState(new StringType(modelID));
             * updateChannelState(new StringType(dateCode));
             * updateChannelState(new StringType(locDesc));
             * updateChannelState(new DecimalType(physEnv));
             * updateChannelState(RawType.valueOf(alarmMask));
             */ // get raw type from string

            if (deviceEnabled != null && (boolean) deviceEnabled == true) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }

        } catch (ZigBeeClusterException e) {
            logger.error("Error, can not get basic information for device {}", address);
            e.printStackTrace();
        }
    }

    @Override
    public void handleCommand(Command command) {
        // TODO: implement factory reset command?
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }
        initializeAttributes();
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        // Not needed yet.
    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, Device device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_BASIC, "Basic", "Information"));

        return channels;
    }

}