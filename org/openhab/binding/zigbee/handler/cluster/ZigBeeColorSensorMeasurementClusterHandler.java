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
import org.bubblecloud.zigbee.api.cluster.general.AnalogInput;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of ZigBee Temperature Measurement Cluster
 *
 * @see <a href=
 *      "https://people.ece.cornell.edu/land/courses/ece4760/FinalProjects/s2011/kjb79_ajm232/pmeter/ZigBee%20Cluster%20Library.pdf">
 *      ZigBee Cluster Library Specification</a>
 *
 * @author Chris Jackson - Initial Contribution
 * @author Dovydas Girdvainis
 */

// Dovydas - written a method getAttributes() for the attribute try-catch block, implemented handleRefresh(), removed
// handleDispose(), added a check for measurement validity (to see if measured value is in range of allowed temperatures
// and if it is not equal to 0x800), changed unsigned 16 bit integer type to signed 16 bit integer, changed
// handleCommand() method to return measurement, changed receivedReport() method to send real temperature instead of
// measured value and to check for invalid temperature.
public class ZigBeeColorSensorMeasurementClusterHandler extends ZigBeeClusterHandler implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeClusterHandler.class);

    private AnalogInput clusColorSensor;
    private Attribute attrPresentValue;

    private Attribute attrValueX;
    private Attribute attrValueY;
    private Attribute attrValueZ;

    private boolean initialised = false;

    private ChannelUID channelX;
    private ChannelUID channelY;
    private ChannelUID channelZ;

    @Override
    public int getClusterId() {
        return ZigBeeApiConstants.CLUSTER_ID_ANALOG_INPUT;
    }

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        attrPresentValue = coordinator.openAttribute(address, AnalogInput.class, Attributes.PRESENT_VALUE, this);
        attrValueX = coordinator.openAttribute(address, AnalogInput.class, Attributes.MEASURED_VALUE_X, this);
        attrValueY = coordinator.openAttribute(address, AnalogInput.class, Attributes.MEASURED_VALUE_Y, this);
        attrValueZ = coordinator.openAttribute(address, AnalogInput.class, Attributes.MEASURED_VALUE_Z, this);

        clusColorSensor = coordinator.openCluster(address, AnalogInput.class);

        if (attrPresentValue == null || clusColorSensor == null) {
            logger.error("One of the Temperature Measurement cluster's mandatory attributes, for device {} , is empty",
                    address);
            return;
        }

        try {
            Object oValue = attrPresentValue.getValue();

            Object oValueX = attrValueX.getValue();
            Object oValueY = attrValueX.getValue();
            Object oValueZ = attrValueX.getValue();

            if (oValue != null) {
                // updateChannelState(new DecimalType(iValue));
                updateChannelState(
                        new StringType("Present: " + oValue.toString() + "<br />" + " X: " + oValueX.toString()
                                + "<br />" + " Y: " + oValueY.toString() + "<br />" + " Z: " + oValueZ.toString()));
            } else {
                updateChannelState(new StringType("null"));
            }
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
        }

        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        if (attrPresentValue != null) {
            coordinator.closeAttribute(attrPresentValue, this);
        }

        if (clusColorSensor != null) {
            coordinator.closeCluster(clusColorSensor);
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
        try {
            Object oValue = attrPresentValue.getValue();

            Object oValueX = attrValueX.getValue();
            Object oValueY = attrValueX.getValue();
            Object oValueZ = attrValueX.getValue();

            if (oValue != null) {
                // updateChannelState(new DecimalType(iValue));
                updateChannelState(
                        new StringType("Present: " + oValue.toString() + "<br />" + " X: " + oValueX.toString()
                                + "<br />" + " Y: " + oValueY.toString() + "<br />" + " Z: " + oValueZ.toString()));
            } else {
                updateChannelState(new StringType("null"));
            }
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleCommand(Command command) {
        if (initialised == false) {
            return;
        }

        try {
            Object oValue = attrPresentValue.getValue();

            Object oValueX = attrValueX.getValue();
            Object oValueY = attrValueX.getValue();
            Object oValueZ = attrValueX.getValue();

            if (oValue != null) {
                // updateChannelState(new DecimalType(iValue));
                updateChannelState(
                        new StringType("Present: " + oValue.toString() + "<br />" + " X: " + oValueX.toString()
                                + "<br />" + " Y: " + oValueY.toString() + "<br />" + " Z: " + oValueZ.toString()));
            } else {
                updateChannelState(new StringType("null"));
            }
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        logger.debug("ZigBee attribute reports {} from {}", reports, endPointId);

        if (attrPresentValue != null) {
            Integer value = (Integer) reports.get(attrPresentValue);

            if (value != null) {
                Integer realTemp = value / 100;
                updateChannelState(new DecimalType(realTemp));
            }
        }
    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, Device device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_COLORTEMPERATURE, "String",
                "FarbtemperaturX"));

        return channels;
    }

}
