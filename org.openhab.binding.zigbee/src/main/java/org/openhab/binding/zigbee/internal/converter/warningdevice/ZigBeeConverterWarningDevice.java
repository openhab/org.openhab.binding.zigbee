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
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.converter.warningdevice.SquawkType;
import org.openhab.binding.zigbee.converter.warningdevice.WarningType;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
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

    private static final String CONFIG_PREFIX = "zigbee_iaswd_";
    private static final String CONFIG_MAXDURATION = CONFIG_PREFIX + "maxDuration";

    private final Logger logger = LoggerFactory.getLogger(ZigBeeConverterWarningDevice.class);

    private ZclIasWdCluster iasWdCluster;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Stream.of(ZclIasWdCluster.CLUSTER_ID).collect(Collectors.toSet());
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        iasWdCluster = (ZclIasWdCluster) endpoint.getInputCluster(ZclIasWdCluster.CLUSTER_ID);
        if (iasWdCluster == null) {
            logger.error("{}: Error opening warning device controls", endpoint.getIeeeAddress());
            return false;
        }

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
        for (Entry<String, Object> updatedParameter : updatedParameters.entrySet()) {
            if (updatedParameter.getKey().startsWith(CONFIG_PREFIX)) {
                if (Objects.equals(updatedParameter.getValue(), currentConfiguration.get(updatedParameter.getKey()))) {
                    logger.debug("Configuration update: Ignored {} as no change", updatedParameter.getKey());
                } else {
                    updateConfigParameter(currentConfiguration, updatedParameter);
                }
            }
        }
    }

    private void updateConfigParameter(Configuration currentConfiguration, Entry<String, Object> updatedParameter) {
        logger.debug("{}: Update IAS WD configuration property {}->{} ({})", iasWdCluster.getZigBeeAddress(),
                updatedParameter.getKey(), updatedParameter.getValue(),
                updatedParameter.getValue().getClass().getSimpleName());

        if (CONFIG_MAXDURATION.equals(updatedParameter.getKey())) {
            iasWdCluster.setMaxDuration(((BigDecimal) (updatedParameter.getValue())).intValue());
            Integer response = iasWdCluster.getMaxDuration(0);

            if (response != null) {
                currentConfiguration.put(updatedParameter.getKey(), BigInteger.valueOf(response));
            }
        } else {
            logger.warn("{}: Unhandled configuration property {}", iasWdCluster.getZigBeeAddress(),
                    updatedParameter.getKey());
        }
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
        iasWdCluster.squawk(
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
