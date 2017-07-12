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
import org.bubblecloud.zigbee.api.cluster.general.Groups;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.api.general.groups.AddGroupResponse;
import org.bubblecloud.zigbee.api.cluster.impl.api.general.groups.GetGroupMembershipResponse;
import org.bubblecloud.zigbee.api.cluster.impl.api.general.groups.RemoveGroupResponse;
import org.bubblecloud.zigbee.api.cluster.impl.api.general.groups.ViewGroupResponse;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.RawType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of ZigBee Groups cluster
 *
 * @see <a href=
 *      "https://people.ece.cornell.edu/land/courses/ece4760/FinalProjects/s2011/kjb79_ajm232/pmeter/ZigBee%20Cluster%20Library.pdf">
 *      ZigBee Cluster Library Specification</a>
 * @author Dovydas Girdvainis
 */

public class ZigBeeGroupsClusterHandler extends ZigBeeClusterHandler implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeClusterHandler.class);

    private final static int NAME_SUPPORT = 128;

    // Mandatory
    private Attribute attrNameSupp;

    private Groups clusGroups;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        attrNameSupp = coordinator.openAttribute(address, Groups.class, Attributes.NAME_SUPPORT_GROUPS, this);

        clusGroups = coordinator.openCluster(address, Groups.class);

        if (attrNameSupp == null || clusGroups == null) {
            logger.error("One of the Groups cluster's mandatory attributes, for device {} , is empty", address);
            return;
        }

        initializeAttributes();

        initialised = true;
    }

    private void initializeAttributes() {
        try {
            String value = (String) attrNameSupp.getValue();
            updateChannelState(RawType.valueOf(value));
        } catch (ZigBeeClusterException e) {
            logger.error("Error, can not get Raw Byte Name support value for endpoint {}", address);
            e.printStackTrace();
        }
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        if (attrNameSupp != null) {
            coordinator.closeAttribute(attrNameSupp, this);
        }
        if (clusGroups != null) {
            coordinator.closeCluster(clusGroups);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }
        initializeAttributes();
    }

    @Override
    public String getStatus() {
        return null;
    }

    @Override
    public void handleCommand(Command command) {
        if (initialised == false) {
            logger.error("Device {} is not initialized.", address);
            return;
        }

        if (clusGroups == null) {
            logger.error("Groups clusster for device {} is not initialized.", address);
            return;
        }

        try {
            if (((Integer) clusGroups.getNameSupport().getValue()) != NAME_SUPPORT) {
                logger.error("Name support for groups for device {} is not allowed.", address);
                return;
            }
        } catch (ZigBeeClusterException e) {
            logger.error("Could not check for group name support for device {} .", address);
            return;
        }

        if (command instanceof AddGroupResponse) {
            try {
                clusGroups.addGroup(((AddGroupResponse) command).getGroupId(), "Group");
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not add group {} for device {} .", ((RemoveGroupResponse) command).getGroupId(),
                        address);
            }
        }
        if (command instanceof GetGroupMembershipResponse) {
            try {
                clusGroups.getGroupMembership(((GetGroupMembershipResponse) command).getGroupList());
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not get a group for device {} .", address);
            }
        }
        if (command instanceof RemoveGroupResponse) {
            try {
                clusGroups.removeGroup(((RemoveGroupResponse) command).getGroupId());
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not remove group {} for device {} .", ((RemoveGroupResponse) command).getGroupId(),
                        address);
            }
        }
        if (command instanceof ViewGroupResponse) {
            try {
                clusGroups.viewGroup(((ViewGroupResponse) command).getGroupId());
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not view group {} for device {} .", ((RemoveGroupResponse) command).getGroupId(),
                        address);
            }
        }
        // TODO: implement addGroupIfIdentifying(int groupId, String name) and removeAllGroup();
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        // Not needed yet
    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, Device device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_GROUPS, "Group", "Group"));

        return channels;
    }

    @Override
    public int getClusterId() {
        return ZigBeeApiConstants.CLUSTER_ID_GROUPS;
    }
}