/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Converter for the thermostat system mode channel. The SystemMode attribute specifies the current operating mode of
 * the thermostat,
 *
 * @author Robert Schmid - Initial Contribution
 *
 */
public class ZigBeeConverterEurotronicSpzb0001WindowOpen extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterEurotronicSpzb0001WindowOpen.class);

    private final static int MANUFACTURER_EUROTRONIC = 0x1037;
    private final static int ATTR_HOST_FLAGS = 0x4008;
    private final static int WINDOW_OPEN_DISABLED = 16;
    private final static int WINDOW_OPEN_ENABLED = 32;

    private ZclThermostatCluster cluster;
    private ZclAttribute attribute;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.singleton(ZclThermostatCluster.CLUSTER_ID);
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    private ZclThermostatCluster getCluster(ZigBeeEndpoint endpoint) {
      ZclThermostatCluster cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
      if (cluster == null) {
        return null;
      }

      ZclAttribute hostFlagsAttribute = new ZclAttribute(cluster, ATTR_HOST_FLAGS, "Host Flags",
        ZclDataType.UNSIGNED_24_BIT_INTEGER, false, true, true, true, MANUFACTURER_EUROTRONIC);
      cluster.addAttributes(new HashSet<>(Arrays.asList(hostFlagsAttribute)));

      return cluster;
    }

    @Override
    public boolean initializeDevice() {
        ZclThermostatCluster serverCluster = getCluster(endpoint);
        if (serverCluster == null) {
          logger.error("{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
          return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ATTR_HOST_FLAGS);
                CommandResult reportingResponse = attribute
                        .setReporting(REPORTING_PERIOD_DEFAULT_MIN, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
            } else {
                logger.debug("{}: Failed to bind thermostat cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }

        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        cluster = getCluster(endpoint);
        if (cluster == null) {
            logger.error("{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
            return false;
        }

        attribute = cluster.getAttribute(ATTR_HOST_FLAGS);
        if (attribute == null) {
            logger.error("{}: Error opening device thermostat Eurotronic host flags attribute", endpoint.getIeeeAddress());
            return false;
        }

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        cluster.removeAttributeListener(this);
    }

    @Override
    public void handleCommand(final Command command) {
        Integer value = null;
        if (command instanceof OnOffType) {
            // OnOff switches between OFF=WINDOW_OPEN_DISABLED and ON=WINDOW_OPEN_ENABLED
            value = command == OnOffType.ON ? WINDOW_OPEN_ENABLED : WINDOW_OPEN_DISABLED;
        }

        if (value == null) {
            logger.warn("{}: Host flags command {} [{}] was not processed", endpoint.getIeeeAddress(), command,
                    command.getClass().getSimpleName());
            return;
        }

        monitorCommandResponse(command, attribute.writeValue(value));
    }

    @Override
    public void handleRefresh() {
        attribute.readValue(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        ZclThermostatCluster cluster = getCluster(endpoint);
        if (cluster == null) {
            logger.trace("{}: Thermostat cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        // Try to read the host flags attribute
        ZclAttribute attribute = cluster.getAttribute(ATTR_HOST_FLAGS);
        Object value = attribute.readValue(Long.MAX_VALUE);
        if (value == null) {
            logger.trace("{}: Thermostat host flags returned null", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint,
                        ZigBeeBindingConstants.CHANNEL_NAME_EUROTRONIC_SPZB001_WINDOW_OPEN),
                        ZigBeeBindingConstants.ITEM_TYPE_SWITCH)
                .withType(ZigBeeBindingConstants.CHANNEL_EUROTRONIC_SPZB001_WINDOW_OPEN)
                .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_EUROTRONIC_SPZB001_WINDOW_OPEN)
                .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.THERMOSTAT
              && attribute.isManufacturerSpecific()
              && attribute.getManufacturerCode() == MANUFACTURER_EUROTRONIC
              && attribute.getId() == ATTR_HOST_FLAGS) {
            Integer value = (Integer) val;
            if (value != null && (value & WINDOW_OPEN_DISABLED) != 0) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}
