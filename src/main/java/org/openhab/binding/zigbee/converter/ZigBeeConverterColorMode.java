/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the color mode indicator.
 * <p>
 * Note that this attribute is not reportable, therefore we set up reporting on other changes to the color and if these
 * change, we can poll the mode.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterColorMode extends ZigBeeChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterColorMode.class);

    private ZclColorControlCluster clusterColorControl;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised) {
            return;
        }

        clusterColorControl = (ZclColorControlCluster) device.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.error("Error opening device control controls {}", device.getIeeeAddress());
            return;
        }

        clusterColorControl.bind();

        // Add a listener, then request the status
        clusterColorControl.addAttributeListener(this);

        clusterColorControl.getColorMode(0);

        clusterColorControl.setColorTemperatureReporting(1, 600, 1);
        clusterColorControl.setCurrentHueReporting(1, 600, 1);
        clusterColorControl.setCurrentSaturationReporting(1, 600, 1);
        clusterColorControl.setCurrentXReporting(1, 600, 1);
        clusterColorControl.setCurrentYReporting(1, 600, 1);

        initialised = true;
    }

    @Override
    public void disposeConverter() {
        clusterColorControl.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        if (!initialised) {
            return;
        }
        clusterColorControl.getColorMode(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint device) {
        if (device.getInputCluster(ZclColorControlCluster.CLUSTER_ID) == null) {
            return null;
        }
        return createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_COLOR_MODE,
                ZigBeeBindingConstants.ITEM_TYPE_NUMBER, "Color Mode");
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, device.getIeeeAddress());
        if (attribute.getCluster() != ZclClusterType.COLOR_CONTROL) {
            return;
        }
        switch (attribute.getId()) {
            case ZclColorControlCluster.ATTR_COLORMODE:
                Integer value = (Integer) attribute.getLastValue();
                if (value != null) {
                    DecimalType decimalValue = new DecimalType(value);
                    updateChannelState(decimalValue);
                }
                break;

            case ZclColorControlCluster.ATTR_COLORTEMPERATURE:
            case ZclColorControlCluster.ATTR_CURRENTHUE:
            case ZclColorControlCluster.ATTR_CURRENTSATURATION:
            case ZclColorControlCluster.ATTR_CURRENTX:
            case ZclColorControlCluster.ATTR_CURRENTY:
                clusterColorControl.getColorMode(0);
                break;
        }
    }

}
