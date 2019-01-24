/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.internal.converter.config.ZclWarningDeviceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasWdCluster;

/**
 * Channel converter for warning devices, based on the IAS WD cluster.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class ZigBeeConverterWarningDevice extends ZigBeeBaseChannelConverter {

    private final Logger logger = LoggerFactory.getLogger(ZigBeeConverterWarningDevice.class);

    private ZclIasWdCluster iasWdCluster;
    private ZclWarningDeviceConfig warningDeviceConfig;

    @Override
    public boolean initializeDevice() {
        return true;
    }

    @Override
    public boolean initializeConverter() {
        iasWdCluster = (ZclIasWdCluster) endpoint.getInputCluster(ZclIasWdCluster.CLUSTER_ID);
        if (iasWdCluster == null) {
            logger.error("{}: Error opening warning device controls", endpoint.getIeeeAddress());
            return false;
        }

        // Create a configuration handler and get the available options
        warningDeviceConfig = new ZclWarningDeviceConfig();
        warningDeviceConfig.setCluster(iasWdCluster);

        return true;
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclIasWdCluster.CLUSTER_ID) == null) {
            logger.trace("{}: IAS WD cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_WARNING_DEVICE),
                        ZigBeeBindingConstants.ITEM_TYPE_STRING)
                .withType(ZigBeeBindingConstants.CHANNEL_WARNING_DEVICE)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_WARNING_DEVICE)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        warningDeviceConfig.updateConfiguration(currentConfiguration, updatedParameters);
    }

    @Override
    public void handleCommand(final Command command) {
        if (iasWdCluster == null) {
            logger.warn("{}: Warning device converter is not linked to a server and cannot accept commands",
                    endpoint.getIeeeAddress());
            return;
        }

        if (!(command instanceof StringType)) {
            logger.warn("{}: This converter only supports string-type commands", endpoint.getIeeeAddress());
            return;
        }

        String commandString = ((StringType) command).toFullString();

        WarningType warningType = WarningType.parse(commandString);
        if (warningType != null) {
            sendWarning(warningType);
        } else {
            SquawkType squawkType = SquawkType.parse(commandString);
            if (squawkType != null) {
                squawk(squawkType);
            } else {
                logger.warn("{}: Ignoring command that is neither warning nor squawk command: {}",
                        endpoint.getIeeeAddress(), commandString);
            }
        }
    }

    private void sendWarning(WarningType warningType) {
        iasWdCluster.startWarningCommand(
                makeWarningHeader(warningType.getWarningMode(), warningType.isUseStrobe(), warningType.getSirenLevel()),
                (int) warningType.getDuration().getSeconds());
    }

    private int makeWarningHeader(int warningMode, boolean useStrobe, int sirenLevel) {
        int result = 0;
        result |= warningMode;
        result |= (useStrobe ? 1 : 0) << 4;
        result |= sirenLevel << 6;
        return result;
    }

    private void squawk(SquawkType squawkType) {
        iasWdCluster.squawkCommand(
                makeSquawkHeader(squawkType.getSquawkMode(), squawkType.isUseStrobe(), squawkType.getSquawkLevel()));
    }

    private Integer makeSquawkHeader(int squawkMode, boolean useStrobe, int squawkLevel) {
        int result = 0;
        result |= squawkMode;
        result |= (useStrobe ? 1 : 0) << 4;
        result |= squawkLevel << 6;
        return result;
    }

}