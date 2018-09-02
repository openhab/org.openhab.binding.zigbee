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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.internal.converter.config.ZclLevelControlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorCapabilitiesEnum;

/**
 * Converter for color control. Uses the {@link ZclColorControlCluster}, and may also use the
 * {@link ZclLevelControlCluster} and {@link ZclOnOffCluster} if available.
 *
 * @author Chris Jackson - Initial Contribution. Improvements in detection of device functionality.
 * @author Pedro Garcia - Added CIE XY color support
 *
 */
public class ZigBeeConverterColorColor extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterColorColor.class);

    private HSBType currentHSB = new HSBType();
    private ZclColorControlCluster clusterColorControl;
    private ZclLevelControlCluster clusterLevelControl;
    private ZclOnOffCluster clusterOnOff;

    private boolean delayedColorChange = false; // Wait for brightness transition before changing color

    private PercentType lastBrightness = PercentType.HUNDRED;
    private ScheduledExecutorService colorUpdateScheduler;
    private ScheduledFuture<?> colorUpdateTimer = null;
    private Object colorUpdateSync = new Object();

    private boolean supportsHue = false;
    private float lastHue = -1.0f;
    private float lastSaturation = -1.0f;
    private boolean hueChanged = false;
    private boolean saturationChanged = false;

    private float lastX = -1.0f;
    private float lastY = -1.0f;
    private boolean xChanged = false;
    private boolean yChanged = false;

    private final AtomicBoolean currentState = new AtomicBoolean(true);

    private ZclLevelControlConfig configLevelControl;

    @Override
    public boolean initializeConverter() {
        colorUpdateScheduler = Executors.newSingleThreadScheduledExecutor();

        clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.error("{}: Error opening device color controls", endpoint.getIeeeAddress());
            return false;
        }

        clusterLevelControl = (ZclLevelControlCluster) endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.warn("{}: Device does not support level control", endpoint.getIeeeAddress());
        }

        clusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            logger.debug("{}: Device does not support on/off control", endpoint.getIeeeAddress());
        }

        // Discover whether the device supports HUE/SAT or XY color set of commands
        try {
            if (!clusterColorControl.discoverAttributes(false).get()) {
                logger.warn("{}: Cannot determine whether device supports RGB color. Assuming it supports HUE/SAT",
                        endpoint.getIeeeAddress());
                supportsHue = true;
            } else if (clusterColorControl.getSupportedAttributes().contains(ZclColorControlCluster.ATTR_CURRENTHUE)) {
                logger.debug("{}: Device supports Hue/Saturation color set of commands", endpoint.getIeeeAddress());
                supportsHue = true;
            } else if (clusterColorControl.getSupportedAttributes().contains(ZclColorControlCluster.ATTR_CURRENTX)) {
                logger.debug("{}: Device supports XY color set of commands", endpoint.getIeeeAddress());
                supportsHue = false;
                delayedColorChange = true; // For now, only for XY lights till this is configurable
            } else {
                logger.warn("{}: Device does not support RGB color", endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn(
                    "{}: Exception checking whether device endpoint supports RGB color. Assuming it supports HUE/SAT",
                    endpoint.getIeeeAddress(), e);
            supportsHue = true;
        }

        // Bind to attribute reports, add listeners, then request the status
        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        clusterColorControl.addAttributeListener(this);

        try {
            CommandResult bindResponse = clusterColorControl.bind().get();
            if (!bindResponse.isSuccess()) {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
            if (supportsHue) {
                CommandResult reportResponse = clusterColorControl
                        .setCurrentHueReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                if (!reportResponse.isSuccess()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
                reportResponse = clusterColorControl.setCurrentSaturationReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1)
                        .get();
                if (!reportResponse.isSuccess()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } else {
                clusterColorControl.setCurrentXReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                clusterColorControl.setCurrentYReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.debug("{}: Exception configuring color reporting", endpoint.getIeeeAddress(), e);
        }

        if (clusterLevelControl != null) {
            clusterLevelControl.addAttributeListener(this);
            try {
                CommandResult bindResponse = clusterLevelControl.bind().get();
                if (!bindResponse.isSuccess()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
                CommandResult reportResponse = clusterLevelControl
                        .setCurrentLevelReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                if (!reportResponse.isSuccess()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } catch (ExecutionException | InterruptedException e) {
                logger.debug("{}: Exception configuring level reporting", endpoint.getIeeeAddress(), e);
            }
        }

        if (clusterOnOff != null) {
            clusterOnOff.addAttributeListener(this);
            try {
                CommandResult bindResponse = clusterOnOff.bind().get();
                if (!bindResponse.isSuccess()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
                CommandResult reportResponse = clusterOnOff.setOnOffReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                if (!reportResponse.isSuccess()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } catch (ExecutionException | InterruptedException e) {
                logger.debug("{}: Exception configuring on/off reporting", endpoint.getIeeeAddress(), e);
            }
        }

        // Create a configuration handler and get the available options
        configLevelControl = new ZclLevelControlConfig(clusterLevelControl);
        configOptions = configLevelControl.getConfiguration();

        return true;
    }

    @Override
    public void disposeConverter() {
        // Stop the timer and shutdown the scheduler
        if (colorUpdateTimer != null) {
            colorUpdateTimer.cancel(true);
            colorUpdateTimer = null;
        }
        colorUpdateScheduler.shutdownNow();

        clusterColorControl.removeAttributeListener(this);

        if (clusterLevelControl != null) {
            clusterLevelControl.removeAttributeListener(this);
        }

        if (clusterOnOff != null) {
            clusterOnOff.removeAttributeListener(this);
        }

        synchronized (colorUpdateSync) {
            if (colorUpdateTimer != null) {
                colorUpdateTimer.cancel(true);
            }
        }
    }

    @Override
    public void handleRefresh() {
        if (clusterOnOff != null) {
            clusterOnOff.getOnOff(0);
        }

        if (supportsHue) {
            clusterColorControl.getCurrentHue(0);
            clusterColorControl.getCurrentSaturation(0);
        } else {
            clusterColorControl.getCurrentX(0);
            clusterColorControl.getCurrentY(0);
        }

        if (clusterLevelControl != null) {
            clusterLevelControl.getCurrentLevel(0);
        }
    }

    private void changeOnOff(OnOffType onoff) throws InterruptedException, ExecutionException {
        boolean on = onoff == OnOffType.ON;
        PercentType brightness = on ? PercentType.HUNDRED : PercentType.ZERO;

        if (clusterLevelControl != null) {
            changeBrightness(brightness);
            return;
        }

        if (clusterOnOff == null) {
            logger.warn("{}: ignoring on/off command", endpoint.getIeeeAddress());
            return;
        }

        HSBType oldHSB = currentHSB;
        currentHSB = new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), brightness);
        lastBrightness = brightness;

        if (on) {
            clusterOnOff.onCommand().get();
        } else {
            clusterOnOff.offCommand().get();
        }
    }

    private void changeBrightness(PercentType brightness) throws InterruptedException, ExecutionException {
        if (clusterLevelControl == null) {
            if (clusterOnOff != null) {
                changeOnOff(brightness.intValue() == 0 ? OnOffType.OFF : OnOffType.ON);
            } else {
                logger.warn("{}: ignoring brightness command", endpoint.getIeeeAddress());
            }
            return;
        }

        HSBType oldHSB = currentHSB;
        currentHSB = new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), brightness);
        lastBrightness = brightness;

        int level = percentToLevel(brightness);

        if (clusterOnOff != null) {
            if(brightness.equals(PercentType.ZERO)) {
                clusterOnOff.offCommand();
            } else {
                clusterLevelControl.moveToLevelWithOnOffCommand(level, configLevelControl.getDefaultTransitionTime()).get();
            }
        } else {
            clusterLevelControl.moveToLevelCommand(level, configLevelControl.getDefaultTransitionTime()).get();
        }
    }

    private void changeColorHueSaturation(HSBType color) throws InterruptedException, ExecutionException {
        HSBType oldHSB = currentHSB;
        currentHSB = new HSBType(color.getHue(), color.getSaturation(), oldHSB.getBrightness());
        int hue = (int) (color.getHue().floatValue() * 254.0f / 360.0f + 0.5f);
        int saturation = percentToLevel(color.getSaturation());

        clusterColorControl
                .moveToHueAndSaturationCommand(hue, saturation, configLevelControl.getDefaultTransitionTime()).get();
    }

    private void changeColorXY(HSBType color) throws InterruptedException, ExecutionException {
        PercentType xy[] = color.toXY();

        HSBType oldHSB = currentHSB;
        currentHSB = new HSBType(color.getHue(), color.getSaturation(), oldHSB.getBrightness());

        logger.debug("{}: Change Color HSV ({}, {}, {}) -> XY ({}, {})", endpoint.getIeeeAddress(), color.getHue(),
                color.getSaturation(), oldHSB.getBrightness(), xy[0], xy[1]);
        int x = (int) (xy[0].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279
        int y = (int) (xy[1].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279

        clusterColorControl.moveToColorCommand(x, y, configLevelControl.getDefaultTransitionTime()).get();
    }

    @Override
    public void handleCommand(final Command command) {
        try {
            if (command instanceof HSBType) {
                HSBType current = currentHSB;
                HSBType color = (HSBType) command;
                PercentType brightness = color.getBrightness();

                boolean changeColor = true;
                if (delayedColorChange) {
                    // Color conversion (HUE -> XY -> HUE) makes this necessary due to rounding & precision
                    int changeSensitivity = supportsHue ? 0 : 1;
                    changeColor = Math.abs(current.getHue().intValue() - color.getHue().intValue()) > changeSensitivity
                            || Math.abs(current.getSaturation().intValue()
                                    - color.getSaturation().intValue()) > changeSensitivity;
                }

                if (brightness.intValue() != currentHSB.getBrightness().intValue()) {
                    changeBrightness(brightness);
                    if (changeColor && delayedColorChange) {
                        Thread.sleep(1100);
                    }
                }

                if (changeColor) {
                    if (supportsHue) {
                        changeColorHueSaturation(color);
                    } else {
                        changeColorXY(color);
                    }
                }
            } else if (command instanceof PercentType) {
                changeBrightness((PercentType) command);
            } else if (command instanceof OnOffType) {
                changeOnOff((OnOffType) command);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception processing command", endpoint.getIeeeAddress(), e);
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclColorControlCluster clusterColorControl = (ZclColorControlCluster) endpoint
                .getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            return null;
        }

        try {
            if (!clusterColorControl.discoverAttributes(false).get()) {
                // Device is not supporting attribute reporting - instead, just read the attributes
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if (capabilities == null && clusterColorControl.getCurrentX(Long.MAX_VALUE) == null
                        && clusterColorControl.getCurrentHue(Long.MAX_VALUE) == null) {
                    return null;
                }
                if (capabilities != null && ((capabilities & (ColorCapabilitiesEnum.HUE_AND_SATURATION.getKey()
                        | ColorCapabilitiesEnum.XY_ATTRIBUTE.getKey())) == 0)) {
                    // No support for hue or XY
                    return null;
                }

            } else if (clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_COLORCAPABILITIES)) {
                // If the device is reporting is capabilities, then use this over attribute detection
                // The color control cluster is required to always support XY attributes, so a non-color bulb is still
                // detected as a color bulb in this case.
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if ((capabilities != null) && (capabilities & (ColorCapabilitiesEnum.HUE_AND_SATURATION.getKey()
                        | ColorCapabilitiesEnum.XY_ATTRIBUTE.getKey())) == 0) {
                    // No support for hue or XY
                    return null;
                }
            } else if (!clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_CURRENTHUE)
                    && !clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_CURRENTX)) {
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("{}: Exception discovering attributes in color control cluster", endpoint.getIeeeAddress(), e);
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_COLOR_COLOR),
                        ZigBeeBindingConstants.ITEM_TYPE_COLOR)
                .withType(ZigBeeBindingConstants.CHANNEL_COLOR_COLOR)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_COLOR_COLOR).withProperties(createProperties(endpoint))
                .build();
    }

    private void updateOnOff(OnOffType onOff) {
        currentState.set(onOff == OnOffType.ON);
        if (onOff == OnOffType.ON) {
            updateBrightness(lastBrightness);
        } else {
            updateChannelState(onOff);
        }
    }

    private void updateBrightness(PercentType brightness) {
        // Extra temp variable to avoid thread sync concurrency issues on currentHSB
        HSBType oldHSB = currentHSB;
        HSBType newHSB = new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), brightness);
        currentHSB = newHSB;
        lastBrightness = brightness;
        if (currentState.get()) {
            updateChannelState(newHSB);
        }
    }

    private void updateColorHSB(DecimalType hue, PercentType saturation) {
        // Extra temp variable to avoid thread sync concurrency issues on currentHSB
        HSBType oldHSB = currentHSB;
        HSBType newHSB = new HSBType(hue, saturation, oldHSB.getBrightness());
        currentHSB = newHSB;
        if (currentState.get()) {
            updateChannelState(newHSB);
        }
    }

    private void updateColorXY(PercentType x, PercentType y) {
        HSBType color = HSBType.fromXY(x.floatValue() / 100.0f, y.floatValue() / 100.0f);
        logger.debug("{}: Update Color XY ({}, {}) -> HSV ({}, {}, {})", endpoint.getIeeeAddress(), x.toString(),
                y.toString(), color.getHue(), color.getSaturation(), currentHSB.getBrightness());
        updateColorHSB(color.getHue(), color.getSaturation());
    }

    private void updateColorHSB() {
        DecimalType hue = new DecimalType(Float.valueOf(lastHue).toString());
        PercentType saturation = new PercentType(Float.valueOf(lastSaturation).toString());
        updateColorHSB(hue, saturation);
        hueChanged = false;
        saturationChanged = false;
    }

    private void updateColorXY() {
        PercentType x = new PercentType(Float.valueOf(lastX * 100.0f).toString());
        PercentType y = new PercentType(Float.valueOf(lastY * 100.0f).toString());
        updateColorXY(x, y);
        xChanged = false;
        yChanged = false;
    }

    @Override
    public Configuration updateConfiguration(@NonNull Configuration configuration) {
        return configLevelControl.updateConfiguration(configuration);
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);

        synchronized (colorUpdateSync) {
            try {
                if (attribute.getCluster().getId() == ZclOnOffCluster.CLUSTER_ID) {
                    if (attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
                        Boolean value = (Boolean) attribute.getLastValue();
                        OnOffType onoff = value ? OnOffType.ON : OnOffType.OFF;
                        updateOnOff(onoff);
                    }
                } else if (attribute.getCluster().getId() == ZclLevelControlCluster.CLUSTER_ID) {
                    if (attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
                        PercentType brightness = levelToPercent((Integer) attribute.getLastValue());
                        updateBrightness(brightness);
                    }
                } else if (attribute.getCluster().getId() == ZclColorControlCluster.CLUSTER_ID) {
                    if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTHUE) {
                        Integer value = (Integer) attribute.getLastValue();
                        float hue = value * 360.0f / 254.0f;
                        if (Math.abs(hue - lastHue) < .0000001) {
                            lastHue = hue;
                            hueChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTSATURATION) {
                        Integer value = (Integer) attribute.getLastValue();
                        float saturation = value * 100.0f / 254.0f;
                        if (Math.abs(saturation - lastSaturation) < .0000001) {
                            lastSaturation = saturation;
                            saturationChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTX) {
                        Integer value = (Integer) attribute.getLastValue();
                        float x = value / 65536.0f;
                        if (Math.abs(x - lastX) < .0000001) {
                            lastX = x;
                            xChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTY) {
                        Integer value = (Integer) attribute.getLastValue();
                        float y = value / 65536.0f;
                        if (Math.abs(y - lastY) < .0000001) {
                            lastY = y;
                            yChanged = true;
                        }
                    }
                }

                if (hueChanged || saturationChanged || xChanged || yChanged) {
                    if (colorUpdateTimer != null) {
                        colorUpdateTimer.cancel(true);
                        colorUpdateTimer = null;
                    }

                    if (hueChanged && saturationChanged) {
                        updateColorHSB();
                    } else if (xChanged && yChanged) {
                        updateColorXY();
                    } else {
                        // Wait some time and update anyway if only one attribute in each pair is updated
                        colorUpdateTimer = colorUpdateScheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                synchronized (colorUpdateSync) {
                                    try {
                                        if ((hueChanged || saturationChanged) && lastHue >= 0.0f
                                                && lastSaturation >= 0.0f) {
                                            updateColorHSB();
                                        } else if ((xChanged || yChanged) && lastX >= 0.0f && lastY >= 0.0f) {
                                            updateColorXY();
                                        }
                                    } catch (Exception e) {
                                        logger.debug("{}: Exception in deferred attribute update",
                                                endpoint.getIeeeAddress(), e);
                                    }

                                    colorUpdateTimer = null;
                                }
                            }
                        }, 500, TimeUnit.MILLISECONDS);
                    }
                }
            } catch (Exception e) {
                logger.debug("{}: Exception in attribute update", endpoint.getIeeeAddress(), e);
            }
        }
    }
}
