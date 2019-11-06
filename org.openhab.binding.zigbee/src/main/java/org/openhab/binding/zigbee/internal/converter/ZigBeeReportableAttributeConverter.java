/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 * <p>
 * See the NOTICE file(s) distributed with this work for additional information.
 * <p>
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.binding.zigbee.internal.converter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.internal.converter.config.ZclReportingConfig;
import org.slf4j.Logger;

/**
 * This class provides a basic infrastructure for handling channels that represent reportable attributes.
 * For analogue types, only integer  change thresholds are supported
 */
abstract class ZigBeeReportableAttributeConverter extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    protected final int attributeId;
    protected final int clusterId;
    protected final String channelNameForLogging;
    protected final String channelName;
    protected final String itemType;
    protected final ChannelTypeUID channelTypeUID;
    protected final String label;
    protected final boolean isAnalogue;
    protected final Logger logger;
    private final BigDecimal changeDefault;
    private final BigDecimal changeMin;
    private final BigDecimal changeMax;
    private ZclCluster cluster;
    private ZclAttribute attribute;
    private ZclReportingConfig configReporting;

    protected ZigBeeReportableAttributeConverter(int attributeId, int clusterId, String channelName, String label,
            String itemType, ChannelTypeUID channelTypeUID, boolean isAnalogue, BigDecimal changeDefault,
            BigDecimal changeMin, BigDecimal changeMax, Logger logger, String channelNameForLogging) {
        this.attributeId = attributeId;
        this.clusterId = clusterId;
        this.channelNameForLogging = channelNameForLogging;
        this.channelName = channelName;
        this.itemType = itemType;
        this.channelTypeUID = channelTypeUID;
        this.label = label;
        this.isAnalogue = isAnalogue;
        this.logger = logger;
        this.changeDefault = changeDefault;
        this.changeMin = changeMin;
        this.changeMax = changeMax;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster().getId() == clusterId && attribute.getId() == attributeId) {
            updateChannelState(convertValueToState((Integer) val));
        }
    }

    @Override
    public boolean initializeDevice() {
        logger.warn("init device");
        ZclCluster serverCluster = endpoint.getInputCluster(clusterId);
        if (serverCluster == null) {
            logger.error("{}: Error opening device {} cluster", endpoint.getIeeeAddress(), channelNameForLogging);
            return false;
        }

        ZclReportingConfig reporting = new ZclReportingConfig(channel);

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(attributeId);
                CommandResult reportingResponse = attribute.setReporting(reporting.getReportingTimeMin(),
                        reporting.getReportingTimeMax(), reporting.getReportingChange()).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, reporting.getPollingPeriod());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.debug("{}: Exception configuring measured value reporting", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        logger.warn("init convert");
        cluster = endpoint.getInputCluster(clusterId);
        if (cluster == null) {
            logger.error("{}: Error opening device {} cluster", endpoint.getIeeeAddress(), channelNameForLogging);
            return false;
        }

        attribute = cluster.getAttribute(attributeId);
        if (attribute == null) {
            logger.error("{}: Error opening device {} attribute", endpoint.getIeeeAddress(), channelNameForLogging);
            return false;
        }

        // Add a listener, then request the status
        cluster.addAttributeListener(this);

        // Create a configuration handler and get the available options
        configReporting = new ZclReportingConfig(channel);
        if (isAnalogue) {
            configReporting.setAnalogue(changeDefault, changeMin, changeMax);
        }
        configOptions = new ArrayList<>();
        configOptions.addAll(configReporting.getConfiguration());

        return true;
    }

    @Override
    public void disposeConverter() {
        cluster.removeAttributeListener(this);
    }

    @Override
    public int getPollingPeriod() {
        return configReporting.getPollingPeriod();
    }

    @Override
    public void handleRefresh() {
        attribute.readValue(0);
    }

    @Override
    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        if (configReporting.updateConfiguration(currentConfiguration, updatedParameters)) {
            try {
                ZclAttribute attribute = cluster.getAttribute(attributeId);
                CommandResult reportingResponse;
                reportingResponse = attribute.setReporting(configReporting.getReportingTimeMin(),
                        configReporting.getReportingTimeMax(), configReporting.getReportingChange()).get();
                handleReportingResponse(reportingResponse, configReporting.getPollingPeriod(),
                        configReporting.getReportingTimeMax());
            } catch (InterruptedException | ExecutionException e) {
                logger.debug("{}: {} exception setting reporting", endpoint.getIeeeAddress(), channelNameForLogging, e);
            }
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(clusterId) == null) {
            logger.trace("{}: {} cluster not found", endpoint.getIeeeAddress(), channelNameForLogging);
            return null;
        }
        return ChannelBuilder.create(createChannelUID(thingUID, endpoint, channelName), itemType).withType(
                channelTypeUID).withLabel(label).withProperties(createProperties(endpoint)).build();
    }

    public abstract State convertValueToState(Integer val);
}
