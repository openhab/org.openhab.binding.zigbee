/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.zigbee.internal.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.internal.converter.config.ZclColorControlConfig;
import org.openhab.binding.zigbee.internal.converter.config.ZclColorControlConfig.ControlMethod;
import org.openhab.binding.zigbee.internal.converter.config.ZclLevelControlConfig;
import org.openhab.binding.zigbee.internal.converter.config.ZclOnOffSwitchConfig;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.UnDefType;
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
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.MoveToColorCommand;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.MoveToHueAndSaturationCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.MoveToLevelWithOnOffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.ZclLevelControlCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ZclOnOffCommand;

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

    private ZclColorControlConfig configColorControl;
    private ZclLevelControlConfig configLevelControl;
    private ZclOnOffSwitchConfig configOnOffSwitch;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Stream
                .of(ZclOnOffCluster.CLUSTER_ID, ZclLevelControlCluster.CLUSTER_ID, ZclColorControlCluster.CLUSTER_ID)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        ZclColorControlCluster serverClusterColorControl = (ZclColorControlCluster) endpoint
                .getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (serverClusterColorControl == null) {
            logger.error("{}: Error opening device color controls", endpoint.getIeeeAddress());
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
                            .setReporting(ZclColorControlCluster.ATTR_CURRENTHUE, 1, REPORTING_PERIOD_DEFAULT_MAX, 1)
                            .get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);

                    reportingResponse = serverClusterColorControl.setReporting(
                            ZclColorControlCluster.ATTR_CURRENTSATURATION, 1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
                } else {
                    reportingResponse = serverClusterColorControl
                            .setReporting(ZclColorControlCluster.ATTR_CURRENTX, 1, REPORTING_PERIOD_DEFAULT_MAX, 1)
                            .get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);

                    reportingResponse = serverClusterColorControl
                            .setReporting(ZclColorControlCluster.ATTR_CURRENTY, 1, REPORTING_PERIOD_DEFAULT_MAX, 1)
                            .get();
                    handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
                }

                reportingResponse = serverClusterColorControl
                        .setReporting(ZclColorControlCluster.ATTR_COLORMODE, 1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
            } else {
                logger.error("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
                pollingPeriod = POLLING_PERIOD_HIGH;
                return false;
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.debug("{}: Exception configuring color reporting", endpoint.getIeeeAddress(), e);
        }

        ZclLevelControlCluster serverClusterLevelControl = (ZclLevelControlCluster) endpoint
                .getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (serverClusterLevelControl == null) {
            logger.warn("{}: Device does not support level control", endpoint.getIeeeAddress());
        } else {
            try {
                CommandResult bindResponse = bind(serverClusterLevelControl).get();
                if (!bindResponse.isSuccess()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }

                CommandResult reportingResponse = serverClusterLevelControl
                        .setReporting(ZclLevelControlCluster.ATTR_CURRENTLEVEL, 1, REPORTING_PERIOD_DEFAULT_MAX, 1)
                        .get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
            } catch (ExecutionException | InterruptedException e) {
                logger.debug("{}: Exception configuring level reporting", endpoint.getIeeeAddress(), e);
            }
        }

        ZclOnOffCluster serverClusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (serverClusterOnOff == null) {
            logger.debug("{}: Device does not support on/off control", endpoint.getIeeeAddress());
        } else {
            try {
                CommandResult bindResponse = bind(serverClusterOnOff).get();
                if (!bindResponse.isSuccess()) {
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
                CommandResult reportingResponse = serverClusterOnOff
                        .setReporting(ZclOnOffCluster.ATTR_ONOFF, 1, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_HIGH, REPORTING_PERIOD_DEFAULT_MAX);
            } catch (ExecutionException | InterruptedException e) {
                logger.debug("{}: Exception configuring on/off reporting", endpoint.getIeeeAddress(), e);
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        colorUpdateScheduler = Executors.newSingleThreadScheduledExecutor();
        clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.error("{}: Error opening device color controls", endpoint.getIeeeAddress());
            return false;
        }

        configOptions = new ArrayList<>();

        clusterLevelControl = (ZclLevelControlCluster) endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.warn("{}: Device does not support level control", endpoint.getIeeeAddress());
        } else {
            configLevelControl = new ZclLevelControlConfig();
            configLevelControl.initialize(clusterLevelControl);
            configOptions.addAll(configLevelControl.getConfiguration());
        }

        clusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            logger.debug("{}: Device does not support on/off control", endpoint.getIeeeAddress());
        } else {
            configOnOffSwitch = new ZclOnOffSwitchConfig();
            configOnOffSwitch.initialize(clusterOnOff);
            configOptions.addAll(configOnOffSwitch.getConfiguration());
        }

        // Create a configuration handler and get the available options
        configColorControl = new ZclColorControlConfig(channel);
        configColorControl.initialize(clusterColorControl);
        configOptions.addAll(configColorControl.getConfiguration());

        if (!discoverSupportedColorCommands(clusterColorControl)) {
            return false;
        }

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
            clusterOnOff.readAttribute(ZclOnOffCluster.ATTR_ONOFF);
        }

        List<Integer> colorAttributes = new ArrayList<>();
        if (supportsHue) {
            colorAttributes.add(ZclColorControlCluster.ATTR_CURRENTHUE);
            colorAttributes.add(ZclColorControlCluster.ATTR_CURRENTSATURATION);
        } else {
            colorAttributes.add(ZclColorControlCluster.ATTR_CURRENTX);
            colorAttributes.add(ZclColorControlCluster.ATTR_CURRENTY);
        }
        colorAttributes.add(ZclColorControlCluster.ATTR_COLORMODE);
        clusterColorControl.readAttributes(colorAttributes);

        if (clusterLevelControl != null) {
            clusterLevelControl.readAttribute(ZclLevelControlCluster.ATTR_CURRENTLEVEL);
        }
    }

    private Future<CommandResult> changeOnOff(OnOffType onoff) throws InterruptedException, ExecutionException {
        boolean on = onoff == OnOffType.ON;

        if (clusterOnOff == null) {
            if (clusterLevelControl == null) {
                logger.warn("{}: ignoring on/off command", endpoint.getIeeeAddress());

                return null;
            } else {
                return changeBrightness(on ? PercentType.HUNDRED : PercentType.ZERO);
            }
        }

        ZclOnOffCommand command;
        if (on) {
            command = new OnCommand();
        } else {
            command = new OffCommand();
        }

        return clusterOnOff.sendCommand(command);
    }

    private Future<CommandResult> changeBrightness(PercentType brightness)
            throws InterruptedException, ExecutionException {
        if (clusterLevelControl == null) {
            if (clusterOnOff == null) {
                logger.warn("{}: ignoring brightness command", endpoint.getIeeeAddress());
                return null;
            } else {
                return changeOnOff(brightness.intValue() == 0 ? OnOffType.OFF : OnOffType.ON);
            }
        }

        int level = percentToLevel(brightness);

        ZclLevelControlCommand command;
        if (clusterOnOff != null) {
            if (brightness.equals(PercentType.ZERO)) {
                return clusterOnOff.sendCommand(new OffCommand());
            } else {
                command = new MoveToLevelWithOnOffCommand(level, configLevelControl.getDefaultTransitionTime());
            }
        } else {
            command = new MoveToLevelCommand(level, configLevelControl.getDefaultTransitionTime());
        }

        return clusterLevelControl.sendCommand(command);
    }

    private Future<CommandResult> changeColorHueSaturation(HSBType color)
            throws InterruptedException, ExecutionException {
        int hue = (int) (color.getHue().floatValue() * 254.0f / 360.0f + 0.5f);
        int saturation = percentToLevel(color.getSaturation());

        return clusterColorControl.sendCommand(
                new MoveToHueAndSaturationCommand(hue, saturation, configLevelControl.getDefaultTransitionTime()));
    }

    private Future<CommandResult> changeColorXY(HSBType color) throws InterruptedException, ExecutionException {
        PercentType xy[] = color.toXY();

        logger.debug("{}: Change Color HSV ({}, {}, {}) -> XY ({}, {})", endpoint.getIeeeAddress(), color.getHue(),
                color.getSaturation(), color.getBrightness(), xy[0], xy[1]);
        int x = (int) (xy[0].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279
        int y = (int) (xy[1].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279

        return clusterColorControl
                .sendCommand(new MoveToColorCommand(x, y, configLevelControl.getDefaultTransitionTime()));
    }

    @Override
    public void handleCommand(final Command command) {
        try {
            List<Future<CommandResult>> futures = new ArrayList<>();
            if (command instanceof HSBType) {
                HSBType color = (HSBType) command;
                PercentType brightness = color.getBrightness();

                futures.add(changeBrightness(brightness));

                if (delayedColorChange && brightness.intValue() != lastHSB.getBrightness().intValue()) {
                    Thread.sleep(1100);
                }

                if (supportsHue) {
                    futures.add(changeColorHueSaturation(color));
                } else {
                    futures.add(changeColorXY(color));
                }
            } else if (command instanceof PercentType) {
                futures.add(changeBrightness((PercentType) command));
            } else if (command instanceof OnOffType) {
                futures.add(changeOnOff((OnOffType) command));
            }

            super.monitorCommandResponse(command, futures);
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

        // Device is not supporting attribute reporting - instead, just read the attributes
        Integer capabilities = (Integer) clusterColorControl.getAttribute(ZclColorControlCluster.ATTR_COLORCAPABILITIES)
                .readValue(Long.MAX_VALUE);
        if (capabilities == null
                && clusterColorControl.getAttribute(ZclColorControlCluster.ATTR_CURRENTX)
                        .readValue(Long.MAX_VALUE) == null
                && clusterColorControl.getAttribute(ZclColorControlCluster.ATTR_CURRENTHUE)
                        .readValue(Long.MAX_VALUE) == null) {
            logger.trace("{}: Color control XY and Hue returned null", endpoint.getIeeeAddress());
            return null;
        }
        if (capabilities != null && ((capabilities & (ColorCapabilitiesEnum.HUE_AND_SATURATION.getKey()
                | ColorCapabilitiesEnum.XY_ATTRIBUTE.getKey())) == 0)) {
            // No support for hue or XY
            logger.trace("{}: Color control XY and Hue capabilities not supported", endpoint.getIeeeAddress());
            return null;
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

        if (lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
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
        if (currentOnOffState.get() && lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
            updateChannelState(newHSB);
        }
    }

    private void updateColorHSB(DecimalType hue, PercentType saturation) {
        // Extra temp variable to avoid thread sync concurrency issues on lastHSB
        HSBType oldHSB = lastHSB;
        HSBType newHSB = new HSBType(hue, saturation, oldHSB.getBrightness());
        lastHSB = newHSB;
        if (currentOnOffState.get() && lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
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
        configColorControl.updateConfiguration(currentConfiguration, updatedParameters);
        configOnOffSwitch.updateConfiguration(currentConfiguration, updatedParameters);
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);

        synchronized (colorUpdateSync) {
            try {
                if (attribute.getClusterType().getId() == ZclOnOffCluster.CLUSTER_ID) {
                    if (attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
                        Boolean value = (Boolean) val;
                        OnOffType onoff = value ? OnOffType.ON : OnOffType.OFF;
                        updateOnOff(onoff);
                    }
                } else if (attribute.getClusterType().getId() == ZclLevelControlCluster.CLUSTER_ID) {
                    if (attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
                        PercentType brightness = levelToPercent((Integer) val);
                        updateBrightness(brightness);
                    }
                } else if (attribute.getClusterType().getId() == ZclColorControlCluster.CLUSTER_ID) {
                    if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTHUE) {
                        int hue = (Integer) val;
                        if (hue != lastHue) {
                            lastHue = hue;
                            hueChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTSATURATION) {
                        int saturation = (Integer) val;
                        if (saturation != lastSaturation) {
                            lastSaturation = saturation;
                            saturationChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTX) {
                        int x = (Integer) val;
                        if (x != lastX) {
                            lastX = x;
                            xChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTY) {
                        int y = (Integer) val;
                        if (y != lastY) {
                            lastY = y;
                            yChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_COLORMODE) {
                        Integer colorMode = (Integer) val;
                        lastColorMode = ColorModeEnum.getByValue(colorMode);
                        if (lastColorMode == ColorModeEnum.COLOR_TEMPERATURE) {
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
        // If the configuration is not set to AUTO, then we can override the control method
        if (configColorControl != null && configColorControl.getControlMethod() != ControlMethod.AUTO) {
            supportsHue = configColorControl.getControlMethod() == ControlMethod.HUE;
            return true;
        }

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
