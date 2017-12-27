/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;

/**
 *
 * @author Chris Jackson - Initial Contribution
 * @author Pedro Garcia - Added CIE XY color support
 *
 */
public class ZigBeeConverterColorColor extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterColorColor.class);

    private HSBType currentHSB;
    private ZclColorControlCluster clusterColorControl;
    private ZclLevelControlCluster clusterLevelControl;
    private ZclOnOffCluster clusterOnOff;

    private boolean initialised = false;
    private PercentType lastBrightness = PercentType.HUNDRED;
    private Object colorUpdateSync = new Object();

    private boolean supportsHue = false;
    private float lastHue = -1.0f;
    private float lastSaturation = -1.0f;
    private boolean hueChanged = false;
    private boolean saturationChanged = false;

    private boolean supportsXY = false;
    private float lastX = -1.0f;
    private float lastY = -1.0f;
    private boolean xChanged = false;
    private boolean yChanged = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        currentHSB = new HSBType();

        clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            logger.error("Error opening device color controls {}", endpoint.getIeeeAddress());
            return;
        }

        clusterLevelControl = (ZclLevelControlCluster) endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            logger.warn("Device does not support level control {}", endpoint.getIeeeAddress());
        }

        clusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            logger.warn("Device does not support on/off control {}", endpoint.getIeeeAddress());
        }

        // Discover whether the device supports HUE/SAT or XY color set of commands
        try {
            if (!clusterColorControl.discoverAttributes(false).get()) {
                logger.warn("{}: Cannot determine whether device supports RGB color. Assuming it supports HUE/SAT",
                        endpoint.getIeeeAddress());
                supportsHue = true;
            } else if (clusterColorControl.getSupportedAttributes().contains(ZclColorControlCluster.ATTR_CURRENTHUE)) {
                logger.debug("Device supports Hue/Saturation color set of commands {}", endpoint.getIeeeAddress());
                supportsHue = true;
            } else if (clusterColorControl.getSupportedAttributes().contains(ZclColorControlCluster.ATTR_CURRENTX)) {
                logger.debug("Device supports XY color set of commands {}", endpoint.getIeeeAddress());
                supportsXY = true;
            } else {
                logger.warn("Device does not support RGB color {}", endpoint.getIeeeAddress());
                return;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn(
                    "{}: Exception checking whether device endpoint supports RGB color. Assuming it supports HUE/SAT",
                    endpoint.getIeeeAddress(), e);
            supportsHue = true;
        }

        // Bind to attribute reports, add listeners, then request the status
        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        clusterColorControl.bind();
        clusterColorControl.addAttributeListener(this);
        if (supportsHue) {
            clusterColorControl.getCurrentHue(0);
            clusterColorControl.getCurrentSaturation(0);
            try {
                clusterColorControl.setCurrentHueReporting(1, 600, 1).get();
                clusterColorControl.setCurrentSaturationReporting(1, 600, 1).get();
            } catch (ExecutionException | InterruptedException e) {
                logger.debug("Exception configuring color reporting", e);
            }
        } else if (supportsXY) {
            clusterColorControl.getCurrentX(0);
            clusterColorControl.getCurrentY(0);
            try {
                clusterColorControl.setCurrentXReporting(1, 600, 1).get();
                clusterColorControl.setCurrentYReporting(1, 600, 1).get();
            } catch (ExecutionException | InterruptedException e) {
                logger.debug("Exception configuring color reporting", e);
            }
        }

        if (clusterLevelControl != null) {
            clusterLevelControl.bind();
            clusterLevelControl.addAttributeListener(this);
            clusterLevelControl.getCurrentLevel(0);
            try {
                clusterLevelControl.setCurrentLevelReporting(1, 600, 1).get();
            } catch (ExecutionException | InterruptedException e) {
                logger.debug("Exception configuring level reporting", e);
            }
        }

        if (clusterOnOff != null) {
            clusterOnOff.bind();
            clusterOnOff.addAttributeListener(this);
            clusterOnOff.getOnOff(0);
            try {
                clusterOnOff.setOnOffReporting(1, 600).get();
            } catch (ExecutionException | InterruptedException e) {
                logger.debug("Exception configuring on/off reporting", e);
            }
        }

        initialised = true;
    }

    @Override
    public void disposeConverter() {
        clusterColorControl.removeAttributeListener(this);

        if (clusterLevelControl != null) {
            clusterLevelControl.removeAttributeListener(this);
        }

        if (clusterOnOff != null) {
            clusterOnOff.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        if (supportsHue) {
            clusterColorControl.getCurrentHue(0);
            clusterColorControl.getCurrentSaturation(0);
        } else if (supportsXY) {
            clusterColorControl.getCurrentX(0);
            clusterColorControl.getCurrentY(0);
        }

        if (clusterLevelControl != null) {
            clusterLevelControl.getCurrentLevel(0);
        }

        if (clusterOnOff != null) {
            clusterOnOff.getOnOff(0);
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

        int level = (int) (brightness.floatValue() * 254.0f / 100.0f + 0.5f);

        if (clusterOnOff != null) {
            clusterLevelControl.moveToLevelWithOnOffCommand(level, 10).get();
        } else {
            clusterLevelControl.moveToLevelCommand(level, 10).get();
        }
    }

    private void changeColorHueSaturation(HSBType color) throws InterruptedException, ExecutionException {
        HSBType oldHSB = currentHSB;
        currentHSB = new HSBType(color.getHue(), color.getSaturation(), oldHSB.getBrightness());
        int hue = (int) (color.getHue().floatValue() * 254.0f / 360.0f + 0.5f);
        int saturation = (int) (color.getSaturation().floatValue() * 254.0f / 100.0f + 0.5f);

        clusterColorControl.moveToHueAndSaturationCommand(hue, saturation, 10).get();
    }

    private void changeColorXY(HSBType color) throws InterruptedException, ExecutionException {
        PercentType xy[] = ColorHelper.toXY(color);

        HSBType oldHSB = currentHSB;
        currentHSB = new HSBType(color.getHue(), color.getSaturation(), oldHSB.getBrightness());

        logger.debug("{}: Change Color HSV. {}, {}, {}", endpoint.getIeeeAddress(), color.getHue(),
                color.getSaturation(), oldHSB.getBrightness());
        logger.debug("{}: Calculated XY. {}, {}", endpoint.getIeeeAddress(), xy[0], xy[1]);
        int x = (int) (xy[0].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279
        int y = (int) (xy[1].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279

        clusterColorControl.moveToColorCommand(x, y, 10).get();
    }

    @Override
    public Runnable handleCommand(final Command command) {
        return new Runnable() {
            @Override
            public void run() {
                if (initialised == false) {
                    return;
                }

                try {
                    if (command instanceof HSBType) {
                        HSBType color = (HSBType) command;
                        PercentType brightness = color.getBrightness();

                        if (brightness.intValue() != currentHSB.getBrightness().intValue()) {
                            changeBrightness(brightness);
                            // Wait for transition to complete
                            // Some lights do not like receiving a level/color change command
                            // while the previous transition is in progress...
                            Thread.sleep(1000);
                        }

                        if (supportsHue) {
                            changeColorHueSaturation(color);
                        } else if (supportsXY) {
                            changeColorXY(color);
                        }
                    } else if (command instanceof PercentType) {
                        PercentType brightness = (PercentType) command;
                        changeBrightness(brightness);
                    } else if (command instanceof OnOffType) {
                        OnOffType onoff = (OnOffType) command;
                        changeOnOff(onoff);
                    }
                } catch ( /* InterruptedException | ExecutionException | */ Exception | Error e) {
                    logger.warn("{}: Exception processing command", endpoint.getIeeeAddress(), e);
                }
            }
        };
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);

        if (clusterColorControl == null) {
            return null;
        }

        try {
            if (!clusterColorControl.discoverAttributes(false).get()) {
                logger.warn(
                        "{}: Cannot determine whether device supports RGB color. Assuming it does by now (checking again later)",
                        endpoint.getIeeeAddress());
            } else if (!clusterColorControl.getSupportedAttributes().contains(ZclColorControlCluster.ATTR_CURRENTHUE)
                    && !clusterColorControl.getSupportedAttributes().contains(ZclColorControlCluster.ATTR_CURRENTX)) {
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn(
                    "{}: Exception checking whether device supports RGB color. Assuming it does by now (checking again later)",
                    endpoint.getIeeeAddress(), e);
        }

        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_COLOR_COLOR,
                ZigBeeBindingConstants.ITEM_TYPE_COLOR, "Color");
    }

    private void updateOnOff(OnOffType onoff) {
        updateBrightness(lastBrightness);
    }

    private void updateBrightness(PercentType brightness) {
        HSBType oldHSB = currentHSB;
        HSBType newHSB = new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), brightness);
        currentHSB = newHSB;
        lastBrightness = brightness;
        updateChannelState(newHSB);
    }

    private void updateColorHSB(DecimalType hue, PercentType saturation) {
        HSBType oldHSB = currentHSB;
        HSBType newHSB = new HSBType(hue, saturation, oldHSB.getBrightness());
        currentHSB = newHSB;
        updateChannelState(newHSB);
    }

    private void updateColorXY(PercentType x, PercentType y) {
        logger.debug("{}: Update Color XY. {}, {}", endpoint.getIeeeAddress(), x.toString(), y.toString());
        HSBType color = ColorHelper.fromXY(x.floatValue() / 100.0f, y.floatValue() / 100.0f, 1.0f);
        logger.debug("{}: Calculated Hue/Saturation. {}, {}", endpoint.getIeeeAddress(), color.getHue(),
                color.getSaturation());
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
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("ZigBee attribute reports {} from {}", attribute, endpoint.getIeeeAddress());

        synchronized (colorUpdateSync) {
            try {
                if (attribute.getCluster().getId() == ZclOnOffCluster.CLUSTER_ID
                        && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
                    Boolean value = (Boolean) attribute.getLastValue();
                    OnOffType onoff = value ? OnOffType.ON : OnOffType.OFF;
                    updateOnOff(onoff);
                } else if (attribute.getCluster().getId() == ZclLevelControlCluster.CLUSTER_ID
                        && attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
                    Integer value = (Integer) attribute.getLastValue();
                    PercentType brightness = new PercentType(Float.valueOf(value * 100.0f / 254.0f).toString());
                    updateBrightness(brightness);
                } else if (attribute.getCluster().getId() == ZclColorControlCluster.CLUSTER_ID
                        && attribute.getId() == ZclColorControlCluster.ATTR_CURRENTHUE) {
                    Integer value = (Integer) attribute.getLastValue();
                    float hue = value * 360.0f / 254.0f;
                    if (hue != lastHue) {
                        lastHue = hue;
                        hueChanged = true;
                    }
                } else if (attribute.getCluster().getId() == ZclColorControlCluster.CLUSTER_ID
                        && attribute.getId() == ZclColorControlCluster.ATTR_CURRENTSATURATION) {
                    Integer value = (Integer) attribute.getLastValue();
                    float saturation = value * 100.0f / 254.0f;
                    if (saturation != lastSaturation) {
                        lastSaturation = saturation;
                        saturationChanged = true;
                    }
                } else if (attribute.getCluster().getId() == ZclColorControlCluster.CLUSTER_ID
                        && attribute.getId() == ZclColorControlCluster.ATTR_CURRENTX) {
                    Integer value = (Integer) attribute.getLastValue();
                    float x = value / 65536.0f;
                    if (x != lastX) {
                        lastX = x;
                        xChanged = true;
                    }
                } else if (attribute.getCluster().getId() == ZclColorControlCluster.CLUSTER_ID
                        && attribute.getId() == ZclColorControlCluster.ATTR_CURRENTY) {
                    Integer value = (Integer) attribute.getLastValue();
                    float y = value / 65536.0f;
                    if (y != lastY) {
                        lastY = y;
                        yChanged = true;
                    }
                }
                if (hueChanged && saturationChanged) {
                    updateColorHSB();
                } else if (xChanged && yChanged) {
                    updateColorXY();
                } else if (hueChanged || saturationChanged || xChanged || yChanged) {
                    // Wait some time and update anyway if only one attribute in each pair is updated
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                sleep(500);
                                synchronized (colorUpdateSync) {
                                    if ((hueChanged || saturationChanged) && lastHue >= 0.0f
                                            && lastSaturation >= 0.0f) {
                                        updateColorHSB();
                                    } else if ((xChanged || yChanged) && lastX >= 0.0f && lastY >= 0.0f) {
                                        updateColorXY();
                                    }
                                }
                            } catch (Exception e) {
                                logger.debug("{}: Exception in deferred attribute update", endpoint.getIeeeAddress(),
                                        e);
                            }
                        }
                    }.start();
                }
            } catch (Exception e) {
                logger.debug("{}: Exception in attribute update", endpoint.getIeeeAddress(), e);
            }
        }
    }
}
