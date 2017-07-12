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
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.TemperatureMeasurement;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Channel;
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
public class ZigBeeTemperatureMeasurementClusterHandler extends ZigBeeClusterHandler implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeClusterHandler.class);

    // private final static int INVALID_TEMPERATURE = 0x8000;

    private TemperatureMeasurement clusTemperature;
    private Attribute attrTemperature;
    // private Attribute attrMinTemperature;
    // private Attribute attrMaxTemperature;

    private boolean initialised = false;

    @Override
    public int getClusterId() {
        return ZigBeeApiConstants.CLUSTER_ID_TEMPERATURE_MEASUREMENT;
    }

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        attrTemperature = coordinator.openAttribute(address, TemperatureMeasurement.class,
                Attributes.MEASURED_VALUE_SIGNED_16_BIT, this);
        // attrMinTemperature = coordinator.openAttribute(address, DeviceTemperatureConfiguration.class,
        // Attributes.MIN_MEASURED_VALUE_SIGNED_16_BIT, this);
        // attrMaxTemperature = coordinator.openAttribute(address, DeviceTemperatureConfiguration.class,
        // Attributes.MAX_MEASURED_VALUE_SIGNED_16_BIT, this);

        clusTemperature = coordinator.openCluster(address, TemperatureMeasurement.class);

        // if (attrTemperature == null || attrMinTemperature == null || attrMaxTemperature == null || clusTemperature ==
        // null) {
        if (attrTemperature == null || clusTemperature == null) {
            logger.error("One of the Temperature Measurement cluster's mandatory attributes, for device {} , is empty",
                    address);
            return;
        }

        try {
            Object oValue = attrTemperature.getValue();
            int iValue = (int) oValue;
            double dValue = iValue / 100.0;
            if (oValue != null) {
                updateChannelState(new DecimalType(dValue));
            } else {
                updateChannelState(new DecimalType(-1.0));
            }
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
        }

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

        if (attrTemperature != null) {
            coordinator.closeAttribute(attrTemperature, this);
        }
        // if (attrMinTemperature != null) {
        // coordinator.closeAttribute(attrTemperature, this);
        // }
        // if (attrMaxTemperature != null) {
        // coordinator.closeAttribute(attrTemperature, this);
        // }
        if (clusTemperature != null) {
            coordinator.closeCluster(clusTemperature);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }
        try {
            Object oValue = attrTemperature.getValue();
            int iValue = (int) oValue;
            double dValue = iValue / 100.0;
            if (oValue != null) {
                updateChannelState(new DecimalType(dValue));
            } else {
                updateChannelState(new DecimalType(-1.0));
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
            Object oValue = attrTemperature.getValue();
            int iValue = (int) oValue;
            double dValue = iValue / 100.0;
            if (oValue != null) {
                updateChannelState(new DecimalType(dValue));
            } else {
                updateChannelState(new DecimalType(-1.0));
            }
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        logger.debug("ZigBee attribute reports {} from {}", reports, endPointId);

        if (attrTemperature != null) {
            Integer value = (Integer) reports.get(attrTemperature);

            if (value != null) {
                Integer realTemp = value / 100;
                updateChannelState(new DecimalType(realTemp));
            }
        }
    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, Device device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_TEMPERATURE, "Number",
                "Temperatur in °C"));

        return channels;
    }

}
