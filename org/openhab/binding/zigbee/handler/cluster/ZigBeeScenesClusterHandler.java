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
import org.bubblecloud.zigbee.api.cluster.general.Scenes;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.api.general.scenes.GetSceneMembershipResponse;
import org.bubblecloud.zigbee.api.cluster.impl.api.general.scenes.RemoveAllScenesResponse;
import org.bubblecloud.zigbee.api.cluster.impl.api.general.scenes.RemoveSceneResponse;
import org.bubblecloud.zigbee.api.cluster.impl.api.general.scenes.StoreSceneResponse;
import org.bubblecloud.zigbee.api.cluster.impl.api.general.scenes.ViewSceneResponse;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.RawType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of ZigBee Scenes cluster
 *
 * @see <a href=
 *      "https://people.ece.cornell.edu/land/courses/ece4760/FinalProjects/s2011/kjb79_ajm232/pmeter/ZigBee%20Cluster%20Library.pdf">
 *      ZigBee Cluster Library Specification</a>
 * @author Dovydas Girdvainis
 */

public class ZigBeeScenesClusterHandler extends ZigBeeClusterHandler implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeClusterHandler.class);

    // Mandatory attributes
    private Attribute attrSceneCount;
    private Attribute attrCurrentScene;
    private Attribute attrCurrentGroup;
    private Attribute attrSceneValid;
    private Attribute attrNameSupport;

    // TODO: find out where to implement Scene Table

    private Scenes clusScene;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        attrSceneCount = coordinator.openAttribute(address, Scenes.class, Attributes.SCENE_COUNT, this);
        attrCurrentScene = coordinator.openAttribute(address, Scenes.class, Attributes.CURRENT_SCENE, this);
        attrCurrentGroup = coordinator.openAttribute(address, Scenes.class, Attributes.CURRENT_GROUP, this);
        attrSceneValid = coordinator.openAttribute(address, Scenes.class, Attributes.SCENE_VALID, this);
        attrNameSupport = coordinator.openAttribute(address, Scenes.class, Attributes.NAME_SUPPORT_GROUPS, this);

        clusScene = coordinator.openCluster(address, Scenes.class);

        if (attrSceneCount == null || attrCurrentScene == null || attrCurrentGroup == null || attrSceneValid == null
                || attrNameSupport == null || clusScene == null) {
            logger.error("One of the Scenes cluster's mandatory attributes, for device {} , is empty", address);
            return;
        }

        initializeAttributes();

        initialised = true;
    }

    @Override
    public String getStatus() {
        return null;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        if (attrSceneCount != null) {
            coordinator.closeAttribute(attrSceneCount, this);
        }
        if (attrCurrentScene != null) {
            coordinator.closeAttribute(attrCurrentScene, this);
        }
        if (attrCurrentGroup != null) {
            coordinator.closeAttribute(attrCurrentGroup, this);
        }
        if (clusScene != null) {
            coordinator.closeCluster(clusScene);
        }
    }

    void initializeAttributes() {
        try {
            Integer sceneCount = (Integer) attrSceneCount.getValue();
            Integer currentScene = (Integer) attrCurrentScene.getValue();
            Integer currentGroup = (Integer) attrCurrentGroup.getValue();
            Boolean SceneValid = (Boolean) attrSceneValid.getValue(); // Check if it works! Might break!
            String nameSupport = (String) attrNameSupport.getValue();
            // TODO: add LastConfiguredBy at some point (IEEE address)

            updateChannelState(new DecimalType(sceneCount));
            updateChannelState(new DecimalType(currentScene));
            updateChannelState(new DecimalType(currentGroup));
            updateChannelState(RawType.valueOf(nameSupport));

            if (SceneValid) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }

        } catch (ZigBeeClusterException e) {
            logger.error("Error, can not get Raw Byte Name support value for endpoint {}", address);
            e.printStackTrace();
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
    public void handleCommand(Command command) {
        if (initialised == false) {
            logger.error("Device {} is not initialized.", address);
            return;
        }

        if (clusScene == null) {
            logger.error("Scenes clusster for device {} is not initialized.", address);
            return;
        }

        // if (command instanceof AddSceneResponse) {
        // try {
        // clusScene.addScene(command.);
        // }catch (ZigBeeDeviceException e) {
        // logger.error("Could not add a scene for device {} is not initialized.", address);
        // }
        // }
        // TODO: find out how to use AddScenePayload

        if (command instanceof GetSceneMembershipResponse) {
            try {
                clusScene.getSceneMembership(((GetSceneMembershipResponse) command).getGroupId());
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not get scene mebership for group {} clusster for device {} .",
                        ((GetSceneMembershipResponse) command).getGroupId(), address);
            }
        }

        if (command instanceof RemoveAllScenesResponse) {
            try {
                clusScene.removeAllScene(((RemoveAllScenesResponse) command).getGroupId());
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not remove all scenes for group {} clusster for device {} .",
                        ((GetSceneMembershipResponse) command).getGroupId(), address);
            }
        }

        if (command instanceof RemoveSceneResponse) {
            try {
                clusScene.removeScene(((RemoveSceneResponse) command).getGroupId(),
                        ((RemoveSceneResponse) command).getSceneId());
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not remove scene {} for group {} clusster for device {} .",
                        ((RemoveSceneResponse) command).getSceneId(),
                        ((GetSceneMembershipResponse) command).getGroupId(), address);
            }
        }

        if (command instanceof StoreSceneResponse) {
            try {
                clusScene.storeScene(((StoreSceneResponse) command).getGroupId(),
                        ((StoreSceneResponse) command).getSceneId());
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not store scene {} for group {} clusster for device {} .",
                        ((RemoveSceneResponse) command).getSceneId(),
                        ((GetSceneMembershipResponse) command).getGroupId(), address);
            }
        }

        if (command instanceof ViewSceneResponse) {
            try {
                clusScene.viewScene(((ViewSceneResponse) command).getGroupId(),
                        ((ViewSceneResponse) command).getSceneId());
            } catch (ZigBeeDeviceException e) {
                logger.error("Could not view scene {} for group {} clusster for device {} .",
                        ((RemoveSceneResponse) command).getSceneId(),
                        ((GetSceneMembershipResponse) command).getGroupId(), address);
            }
        }
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        // Not needed yet

    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, Device device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SCENES, "Scene", "Scene"));

        return channels;
    }

    @Override
    public int getClusterId() {
        return ZigBeeApiConstants.CLUSTER_ID_SCENES;
    }
}