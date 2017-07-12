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
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.RelativeHumidityMeasurement;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of ZigBee Relative Humidity Measurement Cluster
 *
 * @see <a href=
 *      "https://people.ece.cornell.edu/land/courses/ece4760/FinalProjects/s2011/kjb79_ajm232/pmeter/ZigBee%20Cluster%20Library.pdf">
 *      ZigBee Cluster Library Specification</a>
 *
 * @author Chris Jackson - Initial Contribution
 * @author Dovydas Girdvainis
 */

// Dovydas - changed Signed 16 bit integer to unsigned, added checks for invalid measurements, implemented
// disposeConverter() method, changed RelativeHumidityMeasurementImpl clusHumidity to RelativeHumidityMeasurement
// clusHumidity, moved attribute try-catch block to method getAttributes(), changed handleRefresh(), handleCommand() and
// receivedReport() methods
public class ZigBeeRelativeHumidityMeasurementClusterHandler extends ZigBeeClusterHandler implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeClusterHandler.class);

    // private final static int INVALID_HUMIDITY = 0xffff;

    private Attribute attrHumidity;
    // private Attribute attrMinHumidity;
    // private Attribute attrMaxHumidity;

    private RelativeHumidityMeasurement clusHumidity;

    private boolean initialised = false;

    @Override
    public int getClusterId() {
        return ZigBeeApiConstants.CLUSTER_ID_RELATIVE_HUMIDITY_MEASUREMENT;
    }

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        attrHumidity = coordinator.openAttribute(address, RelativeHumidityMeasurement.class,
                Attributes.MEASURED_VALUE_UNSIGNED_16_BIT, this);
        // attrMinHumidity = coordinator.openAttribute(address, RelativeHumidityMeasurement.class,
        // Attributes.MIN_MEASURED_VALUE_UNSIGNED_16_BIT, this);
        // attrMaxHumidity = coordinator.openAttribute(address, RelativeHumidityMeasurement.class,
        // Attributes.MAX_MEASURED_VALUE_UNSIGNED_16_BIT, this);

        clusHumidity = coordinator.openCluster(address, RelativeHumidityMeasurement.class);

        // if (attrHumidity == null || attrMinHumidity == null || attrMaxHumidity == null || clusHumidity == null) {
        if (attrHumidity == null || clusHumidity == null) {
            logger.error("Error opening device level controls {}", address);
            return;
        }

        try {
            Object oValue = attrHumidity.getValue();
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

        if (attrHumidity != null) {
            coordinator.closeAttribute(attrHumidity, this);
        }
        // if (attrMinHumidity != null) {
        // coordinator.closeAttribute(attrMinHumidity, this);
        // }
        // if (attrMaxHumidity != null) {
        // coordinator.closeAttribute(attrMaxHumidity, this);
        // }
        if (clusHumidity != null) {
            coordinator.closeCluster(clusHumidity);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }

        try {
            Object oValue = attrHumidity.getValue();
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
            Object oValue = attrHumidity.getValue();
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

        if (attrHumidity != null) {
            Integer value = (Integer) reports.get(attrHumidity);

            // if (value != null && value != INVALID_HUMIDITY) {
            if (value != null) {
                Integer realValue = value / 100;
                updateChannelState(new DecimalType(realValue));
            }
        }
    }

    @Override
    public List<Channel> getChannels(ThingUID thingUID, Device device) {
        List<Channel> channels = new ArrayList<Channel>();

        channels.add(createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_HUMIDITY, "Number",
                "Feuchtigkeit in %"));

        return channels;
    }

}
