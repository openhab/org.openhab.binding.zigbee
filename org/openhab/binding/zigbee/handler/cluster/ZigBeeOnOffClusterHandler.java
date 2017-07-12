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
import org.bubblecloud.zigbee.api.cluster.general.OnOff;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeOnOffClusterHandler extends ZigBeeClusterHandler implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeClusterHandler.class);

    private OnOffType currentOnOff = OnOffType.OFF;
    private Attribute attrOnOff;

    private OnOff clusOnOff;

    private boolean initialised = false;

    private String status = null;

    @Override
    public int getClusterId() {
        return ZigBeeApiConstants.CLUSTER_ID_ON_OFF;
    }

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        attrOnOff = coordinator.openAttribute(address, OnOff.class, Attributes.ON_OFF, this);
        clusOnOff = coordinator.openCluster(address, OnOff.class);
        if (attrOnOff == null || clusOnOff == null) {
            logger.error("Error opening device on/off controls {}", address);
            return;
        }

        try {
            Object value = attrOnOff.getValue();
            if (value != null && (boolean) value == true) {
                updateChannelState(OnOffType.ON);
                status = (OnOffType.ON).toString();
            } else {
                updateChannelState(OnOffType.OFF);
                status = (OnOffType.OFF).toString();
            }
        } catch (ZigBeeClusterException e) {
            // e.printStackTrace();
        }

        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        if (attrOnOff != null) {
            coordinator.closeAttribute(attrOnOff, this);
        }
        if (clusOnOff != null) {
            coordinator.closeCluster(clusOnOff);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }

        try {
            Object value = attrOnOff.getValue();
            if (value != null && (boolean) value == true) {
                updateChannelState(OnOffType.ON);
                status = (OnOffType.ON).toString();

            } else {
                updateChannelState(OnOffType.OFF);
                status = (OnOffType.OFF).toString();
            }
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public void handleCommand(Command command) {
        if (initialised == false) {
            return;
        }

        if (command instanceof PercentType) {
            if (((PercentType) command).intValue() == 0) {
                currentOnOff = OnOffType.OFF;
                status = (OnOffType.OFF).toString();
            } else {
                currentOnOff = OnOffType.ON;
                status = (OnOffType.ON).toString();
            }
        } else if (command instanceof OnOffType) {
            currentOnOff = (OnOffType) command;
        }

        if (clusOnOff == null) {
            return;
        }
        try {
            if (currentOnOff == OnOffType.ON) {
                clusOnOff.on();
                status = (OnOffType.ON).toString();
            } else {
                clusOnOff.off();
                status = (OnOffType.OFF).toString();
            }
        } catch (ZigBeeDeviceException e) {
            e.printStackTrace();
        }

        // this.thing.updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        logger.debug("ZigBee attribute reports {} from {}", reports, endPointId);
        if (attrOnOff != null) {
            Object value = reports.get(attrOnOff);
            if (value != null && (boolean) value == true) {
                updateChannelState(OnOffType.ON);
                status = (OnOffType.ON).toString();
            } else {
                updateChannelState(OnOffType.OFF);
                status = (OnOffType.OFF).toString();
            }
        }
    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, Device device) {
        List<Channel> channels = new ArrayList<Channel>();

        String label = "nicht definiert";
        String id = device.getEndpointId();
        if (id.endsWith("/1")) {
            label = "Master Schalter";
        }
        if (id.endsWith("/2")) {
            label = "Schalter Kaltweiß";
        }
        if (id.endsWith("/3")) {
            label = "Schalter Warmweiß";
        }
        if (id.endsWith("/4")) {
            label = "Schalter Gelb";
        }
        if (id.endsWith("/5")) {
            label = "Schalter Rot";
        }
        if (id.endsWith("/6")) {
            label = "Sonnenaufgang";
        }
        if (id.endsWith("/7")) {
            label = "Alarmerkennung Mode";
            return channels;
        }
        if (id.endsWith("/8")) {
            label = "Alarm -- Blinken";
        }
        if (id.endsWith("/9")) {
            label = "Alarmerkennung";
            channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF, "Switch", label));
            return channels;
        }
        if (id.endsWith("/10")) {
            label = "Bewegungsmelder";
            channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF, "Switch", label));
            return channels;
        }
        if (id.endsWith("/11")) {
            label = "Geräuschmelder";
            channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SWITCH_ONOFF, "Switch", label));
            return channels;
        }
        if (id.endsWith("/12")) {
            label = " ";
        }
        if (id.endsWith("/13")) {
            label = "Temperatur Schalter";
            return channels;
        }
        if (id.endsWith("/14")) {
            label = "Druck Schalter";
            return channels;
        }
        if (id.endsWith("/15")) {
            label = "Luftfeuchtigkeit Schalter";
            return channels;
        }
        if (id.endsWith("/16")) {
            label = "Strom LED Schalter";
            return channels;
        }
        if (id.endsWith("/17")) {
            label = "Strom System Schalter";
            return channels;
        }
        if (id.endsWith("/18")) {
            label = "Lichtsensor";
            return channels;
        }

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SWITCH, "Switch", label));

        return channels;
    }

}
