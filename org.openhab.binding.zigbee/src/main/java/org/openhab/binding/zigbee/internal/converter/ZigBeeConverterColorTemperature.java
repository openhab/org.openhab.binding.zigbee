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
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorCapabilitiesEnum;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorModeEnum;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Channel converter for color temperature, converting between the color control cluster and a percent-typed channel.
 *
 * @author Chris Jackson - Initial Contribution
 */
public class ZigBeeConverterColorTemperature extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterColorTemperature.class);

    private ZclColorControlCluster clusterColorControl;

    private double kelvinMin;
    private double kelvinMax;
    private double kelvinRange;

    private ColorModeEnum lastColorMode;

    // Default range of 2000K to 6500K
    private final Integer DEFAULT_MIN_TEMPERATURE_IN_KELVIN = 2000;
    private final Integer DEFAULT_MAX_TEMPERATURE_IN_KELVIN = 6500;

    @Override
    public boolean initializeConverter() {
        clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.error("{}: Error opening color control input cluster on endpoint {}", endpoint.getIeeeAddress(),
                    endpoint.getEndpointId());
            return false;
        }

        Integer minTemperatureInMired = clusterColorControl.getColorTemperatureMin(Long.MAX_VALUE);
        Integer maxTemperatureInMired = clusterColorControl.getColorTemperatureMax(Long.MAX_VALUE);

        // High Mired values correspond to low Kelvin values, hence the max Mired value yields the min Kelvin value
        if (maxTemperatureInMired == null) {
            kelvinMin = DEFAULT_MIN_TEMPERATURE_IN_KELVIN;
        } else {
            kelvinMin = miredToKelvin(maxTemperatureInMired);
        }

        // Low Mired values correspond to high Kelvin values, hence the min Mired value yields the max Kelvin value
        if (minTemperatureInMired == null) {
            kelvinMax = DEFAULT_MAX_TEMPERATURE_IN_KELVIN;
        } else {
            kelvinMax = miredToKelvin(minTemperatureInMired);
        }

        kelvinRange = kelvinMax - kelvinMin;

        bind(clusterColorControl);

        clusterColorControl.addAttributeListener(this);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        clusterColorControl.setColorTemperatureReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1);

        // ColorMode reporting
        ZclAttribute colorModeAttribute = clusterColorControl
                .getAttribute(ZclColorControlCluster.ATTR_COLORMODE);
        clusterColorControl.setReporting(colorModeAttribute, 1, REPORTING_PERIOD_DEFAULT_MAX, 1);

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

    @Override
    public void handleCommand(final Command command) {
        PercentType colorTemperaturePercentage = PercentType.ZERO;
        if (command instanceof PercentType) {
            colorTemperaturePercentage = (PercentType) command;
        } else if (command instanceof OnOffType) {
            // TODO: Should this turn the lamp on/off?
            return;
        }

        clusterColorControl.moveToColorTemperatureCommand(percentToMired(colorTemperaturePercentage), 10);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclColorControlCluster clusterColorControl = (ZclColorControlCluster) endpoint
                .getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.trace("{}: Color control cluster not found on endpoint {}", endpoint.getIeeeAddress(),
                    endpoint.getEndpointId());
            return null;
        }

        try {
            if (!clusterColorControl.discoverAttributes(false).get()) {
                // Device is not supporting attribute reporting - instead, just read the attributes
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if (capabilities == null && clusterColorControl.getColorTemperature(Long.MAX_VALUE) == null) {
                    logger.trace("{}: Color control color temperature attribute returned null on endpoint {}",
                            endpoint.getIeeeAddress(), endpoint.getEndpointId());
                    return null;
                }
                if (capabilities != null && (capabilities & ColorCapabilitiesEnum.COLOR_TEMPERATURE.getKey()) == 0) {
                    // No support for color temperature
                    logger.trace("{}: Color control color temperature capability not supported on endpoint {}",
                            endpoint.getIeeeAddress(), endpoint.getEndpointId());
                    return null;
                }
            } else if (clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_COLORCAPABILITIES)) {
                // If the device is reporting is capabilities, then use this over attribute detection
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if (capabilities != null && (capabilities & ColorCapabilitiesEnum.COLOR_TEMPERATURE.getKey()) == 0) {
                    // No support for color temperature
                    logger.trace("{}: Color control color temperature capability not supported on endpoint {}",
                            endpoint.getIeeeAddress(), endpoint.getEndpointId());
                    return null;
                }
            } else if (!clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_COLORTEMPERATURE)) {
                logger.trace("{}: Color control color temperature attribute not supported on endpoint {}",
                        endpoint.getIeeeAddress(), endpoint.getEndpointId());
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn(String.format("%s: Exception discovering attributes in color control cluster on endpoint %d",
                    endpoint.getIeeeAddress(), endpoint.getEndpointId()), e);
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
        logger.debug("{}: ZigBee attribute reports {}  on endpoint {}", endpoint.getIeeeAddress(), attribute,
                endpoint.getEndpointId());
        if (attribute.getCluster() == ZclClusterType.COLOR_CONTROL
                && attribute.getId() == ZclColorControlCluster.ATTR_COLORTEMPERATURE) {

            if (lastColorMode == null || lastColorMode == ColorModeEnum.COLORTEMPERATURE) {
                Integer temperatureInMired = (Integer) attribute.getLastValue();

                PercentType percent = miredToPercent(temperatureInMired);
                if (percent != null) {
                    updateChannelState(percent);
                }
            }
        } else if (attribute.getCluster() == ZclClusterType.COLOR_CONTROL
                && attribute.getId() == ZclColorControlCluster.ATTR_COLORMODE) {
            Integer colorMode = (Integer) attribute.getLastValue();
            lastColorMode = ColorModeEnum.getByValue(colorMode);
            if (lastColorMode != ColorModeEnum.COLORTEMPERATURE) {
                updateChannelState(UnDefType.UNDEF);
            }
        }
    }

    /**
     * Convert color temperature in Mired to Kelvin.
     */
    private int miredToKelvin(int temperatureInMired) {
        return (int) (1e6 / temperatureInMired);
    }

    /**
     * Convert color temperature in Kelvin to Mired.
     */
    private int kelvinToMired(int temperatureInKelvin) {
        return (int) (1e6 / temperatureInKelvin);
    }

    /**
     * Convert color temperature given as percentage to Kelvin.
     */
    private int percentToKelvin(PercentType temperatureInPercent) {
        double value = ((100.0 - temperatureInPercent.doubleValue()) * kelvinRange / 100.0) + kelvinMin;
        return (int) (value + 0.5);
    }

    /**
     * Convert color temperature given as percentage to Mired.
     */
    private int percentToMired(PercentType temperatureInPercent) {
        return kelvinToMired(percentToKelvin(temperatureInPercent));
    }

    /**
     * Convert color temperature given in Kelvin to percentage.
     */
    private PercentType kelvinToPercent(int temperatureInKelvin) {
        double value = 100.0 - (temperatureInKelvin - kelvinMin) * 100.0 / kelvinRange;
        return new PercentType((int) (value + 0.5));
    }

    /**
     * Convert color temperature given in Mired to percentage.
     */
    private PercentType miredToPercent(Integer temperatureInMired) {
        if (temperatureInMired == null) {
            return null;
        }
        if (temperatureInMired == 0x0000 || temperatureInMired == 0xffff) {
            // 0x0000 indicates undefined value.
            // 0xffff indicates invalid value (possible due to color mode not being CT).
            return null;
        }
        return kelvinToPercent(miredToKelvin(temperatureInMired));
    }

}
