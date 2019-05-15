/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import java.util.Map;
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
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
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
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorModeEnum;

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

    private HSBType lastHSB = new HSBType("0,0,100");
    private ZclColorControlCluster clusterColorControl;
    private ZclLevelControlCluster clusterLevelControl;
    private ZclOnOffCluster clusterOnOff;

    private boolean delayedColorChange = false; // Wait for brightness transition before changing color

    private ScheduledExecutorService colorUpdateScheduler;
    private ScheduledFuture<?> colorUpdateTimer = null;
    private Object colorUpdateSync = new Object();

    private boolean supportsHue = false;

    private int lastHue = -1;
    private int lastSaturation = -1;
    private boolean hueChanged = false;
    private boolean saturationChanged = false;

    private int lastX = -1;
    private int lastY = -1;
    private boolean xChanged = false;
    private boolean yChanged = false;

    private ColorModeEnum lastColorMode;

    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);

    private ZclLevelControlConfig configLevelControl;

    @Override
    public boolean initializeDevice() {
        ZclColorControlCluster serverClusterColorControl = (ZclColorControlCluster) endpoint
                .getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (serverClusterColorControl == null) {
            logger.error("{}: Error opening device color controls", endpoint.getIeeeAddress());
            return false;
        }

        ZclLevelControlCluster serverClusterLevelControl = (ZclLevelControlCluster) endpoint
                .getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (serverClusterLevelControl == null) {
            logger.warn("{}: Device does not support level control", endpoint.getIeeeAddress());
            return false;
        }

        ZclOnOffCluster serverClusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (serverClusterOnOff == null) {
            logger.debug("{}: Device does not support on/off control", endpoint.getIeeeAddress());
            return false;
        }

        if (!discoverSupportedColorCommands(serverClusterColorControl)) {
            return false;
        }

        // Bind to attribute reports, add listeners, then request the status
        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        try {
            CommandResult bindResponse = bind(serverClusterColorControl).get();
            if (bindResponse.isSuccess()) {
                CommandResult reportingResponse;
                if (supportsHue) {
                    reportingResponse = serverClusterColorControl
                            .setCurrentHueReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);

                    reportingResponse = serverClusterColorControl
                            .setCurrentSaturationReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
                } else {
                    reportingResponse = serverClusterColorControl
                            .setCurrentXReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);

                    reportingResponse = serverClusterColorControl
                            .setCurrentYReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
                }
            } else {
                logger.error("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
                pollingPeriod = POLLING_PERIOD_HIGH;
                return false;
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.debug("{}: Exception configuring color reporting", endpoint.getIeeeAddress(), e);
        }

        try {
            CommandResult bindResponse = bind(serverClusterLevelControl).get();
            if (!bindResponse.isSuccess()) {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
            CommandResult reportingResponse = serverClusterLevelControl
                    .setCurrentLevelReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
            handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
        } catch (ExecutionException | InterruptedException e) {
            logger.debug("{}: Exception configuring level reporting", endpoint.getIeeeAddress(), e);
        }

        try {
            CommandResult bindResponse = bind(serverClusterOnOff).get();
            if (!bindResponse.isSuccess()) {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
            CommandResult reportingResponse = serverClusterOnOff.setOnOffReporting(1, REPORTING_PERIOD_DEFAULT_MAX)
                    .get();
            handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
        } catch (ExecutionException | InterruptedException e) {
            logger.debug("{}: Exception configuring on/off reporting", endpoint.getIeeeAddress(), e);
            return false;
        }

        try {
            ZclAttribute colorModeAttribute = serverClusterColorControl
                    .getAttribute(ZclColorControlCluster.ATTR_COLORMODE);
            CommandResult reportingResponse = serverClusterColorControl
                    .setReporting(colorModeAttribute, 1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
            handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
        } catch (ExecutionException | InterruptedException e) {
            logger.debug("{}: Exception configuring color mode reporting", endpoint.getIeeeAddress(), e);
            return false;
        }

        return true;
    }

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

        if (!discoverSupportedColorCommands(clusterColorControl)) {
            return false;
        }

        // Create a configuration handler and get the available options
        configLevelControl = new ZclLevelControlConfig();
        configLevelControl.initialize(clusterLevelControl);
        configOptions = configLevelControl.getConfiguration();

        clusterColorControl.addAttributeListener(this);
        clusterLevelControl.addAttributeListener(this);
        clusterOnOff.addAttributeListener(this);

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

        clusterColorControl.getColorMode(0);
    }

    private void changeOnOff(OnOffType onoff) throws InterruptedException, ExecutionException {
        boolean on = onoff == OnOffType.ON;

        if (clusterOnOff == null) {
            if (clusterLevelControl == null) {
                logger.warn("{}: ignoring on/off command", endpoint.getIeeeAddress());
            } else {
                changeBrightness(on ? PercentType.HUNDRED : PercentType.ZERO);
            }
            return;
        }

        if (on) {
            clusterOnOff.onCommand().get();
        } else {
            clusterOnOff.offCommand().get();
        }
    }

    private void changeBrightness(PercentType brightness) throws InterruptedException, ExecutionException {
        if (clusterLevelControl == null) {
            if (clusterOnOff == null) {
                logger.warn("{}: ignoring brightness command", endpoint.getIeeeAddress());
            } else {
                changeOnOff(brightness.intValue() == 0 ? OnOffType.OFF : OnOffType.ON);
            }
            return;
        }

        int level = percentToLevel(brightness);

        if (clusterOnOff != null) {
            if (brightness.equals(PercentType.ZERO)) {
                clusterOnOff.offCommand();
            } else {
                clusterLevelControl.moveToLevelWithOnOffCommand(level, configLevelControl.getDefaultTransitionTime())
                        .get();
            }
        } else {
            clusterLevelControl.moveToLevelCommand(level, configLevelControl.getDefaultTransitionTime()).get();
        }
    }

    private void changeColorHueSaturation(HSBType color) throws InterruptedException, ExecutionException {
        int hue = (int) (color.getHue().floatValue() * 254.0f / 360.0f + 0.5f);
        int saturation = percentToLevel(color.getSaturation());

        clusterColorControl
                .moveToHueAndSaturationCommand(hue, saturation, configLevelControl.getDefaultTransitionTime()).get();
    }

    private void changeColorXY(HSBType color) throws InterruptedException, ExecutionException {
        PercentType xy[] = color.toXY();

        logger.debug("{}: Change Color HSV ({}, {}, {}) -> XY ({}, {})", endpoint.getIeeeAddress(), color.getHue(),
                color.getSaturation(), color.getBrightness(), xy[0], xy[1]);
        int x = (int) (xy[0].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279
        int y = (int) (xy[1].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279

        clusterColorControl.moveToColorCommand(x, y, configLevelControl.getDefaultTransitionTime()).get();
    }

    @Override
    public void handleCommand(final Command command) {
        try {
            if (command instanceof HSBType) {
                HSBType color = (HSBType) command;
                PercentType brightness = color.getBrightness();

                changeBrightness(brightness);

                if (delayedColorChange && brightness.intValue() != lastHSB.getBrightness().intValue()) {
                    Thread.sleep(1100);
                }

                if (supportsHue) {
                    changeColorHueSaturation(color);
                } else {
                    changeColorXY(color);
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
            logger.trace("{}: Color control cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        try {
            if (!clusterColorControl.discoverAttributes(false).get()) {
                // Device is not supporting attribute reporting - instead, just read the attributes
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if (capabilities == null && clusterColorControl.getCurrentX(Long.MAX_VALUE) == null
                        && clusterColorControl.getCurrentHue(Long.MAX_VALUE) == null) {
                    logger.trace("{}: Color control XY and Hue returned null", endpoint.getIeeeAddress());
                    return null;
                }
                if (capabilities != null && ((capabilities & (ColorCapabilitiesEnum.HUE_AND_SATURATION.getKey()
                        | ColorCapabilitiesEnum.XY_ATTRIBUTE.getKey())) == 0)) {
                    // No support for hue or XY
                    logger.trace("{}: Color control XY and Hue capabilities not supported", endpoint.getIeeeAddress());
                    return null;
                }
            } else if (clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_COLORCAPABILITIES)) {
                // If the device is reporting its capabilities, then use this over attribute detection
                // The color control cluster is required to always support XY attributes, so a non-color bulb is still
                // detected as a color bulb in this case.
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if ((capabilities != null) && (capabilities & (ColorCapabilitiesEnum.HUE_AND_SATURATION.getKey()
                        | ColorCapabilitiesEnum.XY_ATTRIBUTE.getKey())) == 0) {
                    // No support for hue or XY
                    logger.trace("{}: Color control XY and Hue capabilities not supported", endpoint.getIeeeAddress());
                    return null;
                }
            } else if (!clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_CURRENTHUE)
                    && !clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_CURRENTX)) {
                logger.trace("{}: Color control XY and Hue attributes not supported", endpoint.getIeeeAddress());
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
        boolean on = onOff == OnOffType.ON;
        currentOnOffState.set(on);

        if (lastColorMode != ColorModeEnum.COLORTEMPERATURE) {
            // Extra temp variable to avoid thread sync concurrency issues on lastHSB
            HSBType oldHSB = lastHSB;
            HSBType newHSB = on ? lastHSB : new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), PercentType.ZERO);
            updateChannelState(newHSB);
        } else if (!on) {
            updateChannelState(OnOffType.OFF);
        }

    }

    private void updateBrightness(PercentType brightness) {
        // Extra temp variable to avoid thread sync concurrency issues on lastHSB
        HSBType oldHSB = lastHSB;
        HSBType newHSB = new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), brightness);
        lastHSB = newHSB;
        if (currentOnOffState.get() && lastColorMode != ColorModeEnum.COLORTEMPERATURE) {
            updateChannelState(newHSB);
        }
    }

    private void updateColorHSB(DecimalType hue, PercentType saturation) {
        // Extra temp variable to avoid thread sync concurrency issues on lastHSB
        HSBType oldHSB = lastHSB;
        HSBType newHSB = new HSBType(hue, saturation, oldHSB.getBrightness());
        lastHSB = newHSB;
        if (currentOnOffState.get() && lastColorMode != ColorModeEnum.COLORTEMPERATURE) {
            updateChannelState(newHSB);
        }
    }

    private void updateColorXY(PercentType x, PercentType y) {
        HSBType color = HSBType.fromXY(x.floatValue() / 100.0f, y.floatValue() / 100.0f);
        logger.debug("{}: Update Color XY ({}, {}) -> HSV ({}, {}, {})", endpoint.getIeeeAddress(), x.toString(),
                y.toString(), color.getHue(), color.getSaturation(), lastHSB.getBrightness());
        updateColorHSB(color.getHue(), color.getSaturation());
    }

    private void updateColorHSB() {
        float hueValue = lastHue * 360.0f / 254.0f;
        float saturationValue = lastSaturation * 100.0f / 254.0f;
        DecimalType hue = new DecimalType(Float.valueOf(hueValue).toString());
        PercentType saturation = new PercentType(Float.valueOf(saturationValue).toString());
        updateColorHSB(hue, saturation);
        hueChanged = false;
        saturationChanged = false;
    }

    private void updateColorXY() {
        float xValue = lastX / 65536.0f;
        float yValue = lastY / 65536.0f;
        PercentType x = new PercentType(Float.valueOf(xValue * 100.0f).toString());
        PercentType y = new PercentType(Float.valueOf(yValue * 100.0f).toString());
        updateColorXY(x, y);
        xChanged = false;
        yChanged = false;
    }

    @Override
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        configLevelControl.updateConfiguration(currentConfiguration, updatedParameters);
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
                        int hue = ((Integer) attribute.getLastValue()).intValue();
                        if (hue != lastHue) {
                            lastHue = hue;
                            hueChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTSATURATION) {
                        int saturation = ((Integer) attribute.getLastValue()).intValue();
                        if (saturation != lastSaturation) {
                            lastSaturation = saturation;
                            saturationChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTX) {
                        int x = ((Integer) attribute.getLastValue()).intValue();
                        if (x != lastX) {
                            lastX = x;
                            xChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTY) {
                        int y = ((Integer) attribute.getLastValue()).intValue();
                        if (y != lastY) {
                            lastY = y;
                            yChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_COLORMODE) {
                        Integer colorMode = (Integer) attribute.getLastValue();
                        lastColorMode = ColorModeEnum.getByValue(colorMode);
                        if (lastColorMode == ColorModeEnum.COLORTEMPERATURE) {
                            updateChannelState(UnDefType.UNDEF);
                        } else if (currentOnOffState.get()) {
                            updateChannelState(lastHSB);
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

    private boolean discoverSupportedColorCommands(ZclColorControlCluster serverClusterColorControl) {
        // Discover whether the device supports HUE/SAT or XY color set of commands
        try {
            if (!serverClusterColorControl.discoverAttributes(false).get()) {
                logger.warn("{}: Cannot determine whether device supports RGB color. Assuming it supports HUE/SAT",
                        endpoint.getIeeeAddress());
                supportsHue = true;
            } else if (serverClusterColorControl.getSupportedAttributes()
                    .contains(ZclColorControlCluster.ATTR_CURRENTHUE)) {
                logger.debug("{}: Device supports Hue/Saturation color set of commands", endpoint.getIeeeAddress());
                supportsHue = true;
            } else if (serverClusterColorControl.getSupportedAttributes()
                    .contains(ZclColorControlCluster.ATTR_CURRENTX)) {
                logger.debug("{}: Device supports XY color set of commands", endpoint.getIeeeAddress());
                supportsHue = false;
                delayedColorChange = true; // For now, only for XY lights till this is configurable
            } else {
                logger.warn("{}: Device supports neither RGB color nor XY color", endpoint.getIeeeAddress());
                pollingPeriod = POLLING_PERIOD_HIGH;
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn(
                    "{}: Exception checking whether device endpoint supports RGB color. Assuming it supports HUE/SAT",
                    endpoint.getIeeeAddress(), e);
            supportsHue = true;
        }

        return true;
    }

}
