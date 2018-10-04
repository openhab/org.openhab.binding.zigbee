/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorCapabilitiesEnum;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterColorTemperature extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterColorTemperature.class);

    private ZclColorControlCluster clusterColorControl;

    private double kelvinMin;
    private double kelvinMax;
    private double kelvinRange;

    // Default range of 2000K to 6500K
    private final Integer CT_DEFAULT_MIN = 2000;
    private final Integer CT_DEFAULT_MAX = 6500;

    @Override
    public boolean initializeConverter() {
        clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.error("{}: Error opening device control controls", endpoint.getIeeeAddress());
            return false;
        }

        Integer kMin = clusterColorControl.getColorTemperatureMin(Long.MAX_VALUE);
        Integer kMax = clusterColorControl.getColorTemperatureMax(Long.MAX_VALUE);

        if (kMin == null) {
            kelvinMin = CT_DEFAULT_MIN;
        } else {
            kelvinMin = (int) (1e6 / kMin);
        }
        if (kMax == null) {
            kelvinMax = CT_DEFAULT_MAX;
        } else {
            kelvinMax = (int) (1e6 / kMax);
        }
        kelvinRange = kelvinMax - kelvinMin;

        clusterColorControl.bind();

        clusterColorControl.addAttributeListener(this);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        clusterColorControl.setColorTemperatureReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1);

        return true;
    }

    @Override
    public void disposeConverter() {
        clusterColorControl.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        clusterColorControl.getColorTemperature(0);
    }

    private int convertPercentToKelvin(PercentType colorTemp) {
        return (int) (1e6 / ((colorTemp.doubleValue() * kelvinRange / 100.0) + kelvinMin) + 0.5);
    }

    private PercentType convertKelvinToPercent(Integer kelvin) {
        if (kelvin == null) {
            return null;
        }
        if (kelvin == 0x0000 || kelvin == 0xffff) {
            // 0x0000 indicates undefined value.
            // 0xffff indicates invalid value (possible due to color mode not being CT).
            return null;
        }
        return new PercentType((int) (((1e6 / kelvin) - kelvinMin) * 100.0 / kelvinRange + 0.5));
    }

    @Override
    public void handleCommand(final Command command) {
        PercentType colorTemp = PercentType.ZERO;
        if (command instanceof PercentType) {
            colorTemp = (PercentType) command;
        } else if (command instanceof OnOffType) {
            // TODO: Should this turn the lamp on/off?
            return;
        }

        clusterColorControl.moveToColorTemperatureCommand(convertPercentToKelvin(colorTemp), 10);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclColorControlCluster clusterColorControl = (ZclColorControlCluster) endpoint
                .getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.trace("{}: Color control cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        try {
            if (!clusterColorControl.discoverAttributes(false).get()) {
                // Device is not supporting attribute reporting - instead, just read the attributes
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if (capabilities == null && clusterColorControl.getColorTemperature(Long.MAX_VALUE) == null) {
                    logger.trace("{}: Color control color temperature attribute returned null",
                            endpoint.getIeeeAddress());
                    return null;
                }
                if (capabilities != null && (capabilities & ColorCapabilitiesEnum.COLOR_TEMPERATURE.getKey()) == 0) {
                    // No support for color temperature
                    logger.trace("{}: Color control color temperature capability not supported",
                            endpoint.getIeeeAddress());
                    return null;
                }
            } else if (clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_COLORCAPABILITIES)) {
                // If the device is reporting is capabilities, then use this over attribute detection
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if (capabilities != null && (capabilities & ColorCapabilitiesEnum.COLOR_TEMPERATURE.getKey()) == 0) {
                    // No support for color temperature
                    logger.trace("{}: Color control color temperature capability not supported",
                            endpoint.getIeeeAddress());
                    return null;
                }
            } else if (!clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_COLORTEMPERATURE)) {
                logger.trace("{}: Color control color temperature attribute not supported", endpoint.getIeeeAddress());
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in color control cluster", endpoint.getIeeeAddress(), e);
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_COLOR_TEMPERATURE),
                        ZigBeeBindingConstants.ITEM_TYPE_DIMMER)
                .withType(ZigBeeBindingConstants.CHANNEL_COLOR_TEMPERATURE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_COLOR_TEMPERATURE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.COLOR_CONTROL
                && attribute.getId() == ZclColorControlCluster.ATTR_COLORTEMPERATURE) {
            Integer value = (Integer) attribute.getLastValue();
            State state = convertKelvinToPercent(value);
            if (state != null) {
                updateChannelState(state);
            }
        }
    }

}
