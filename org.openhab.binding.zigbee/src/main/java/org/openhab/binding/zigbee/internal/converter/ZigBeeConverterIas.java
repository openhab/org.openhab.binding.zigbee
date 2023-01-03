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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.ZclStatus;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneStatusChangeNotificationCommand;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Converter for the IAS zone sensors. This is an abstract class used as a base for different IAS sensors.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public abstract class ZigBeeConverterIas extends ZigBeeBaseChannelConverter
        implements ZclCommandListener, ZclAttributeListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterIas.class);

    private ZclIasZoneCluster clusterIasZone;

    protected int bitTest = CIE_ALARM1;

    /**
     * CIE Zone Status Attribute flags
     */
    protected static final int CIE_ALARM1 = 0x0001;
    protected static final int CIE_ALARM2 = 0x0002;
    protected static final int CIE_TAMPER = 0x0004;
    protected static final int CIE_BATTERY = 0x0008;
    protected static final int CIE_SUPERVISION = 0x0010;
    protected static final int CIE_RESTORE = 0x0020;
    protected static final int CIE_TROUBLE = 0x0040;
    protected static final int CIE_ACMAINS = 0x0080;
    protected static final int CIE_TEST = 0x0100;
    protected static final int CIE_BATTERYDEFECT = 0x0200;

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Stream.of(ZclIasZoneCluster.CLUSTER_ID).collect(Collectors.toSet());
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.emptySet();
    }

    @Override
    public boolean initializeDevice() {
        logger.debug("{}: Initialising device IAS Zone cluster for {}", endpoint.getIeeeAddress(),
                channel.getChannelTypeUID());

        ZclIasZoneCluster serverClusterIasZone = (ZclIasZoneCluster) endpoint
                .getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        if (serverClusterIasZone == null) {
            logger.error("{}: Error opening IAS zone cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterIasZone).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                ZclAttribute attribute = serverClusterIasZone.getAttribute(ZclIasZoneCluster.ATTR_ZONESTATUS);
                CommandResult reportingResponse = serverClusterIasZone
                        .setReporting(attribute, 3, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.debug("{}: Exception configuring ias zone status reporting", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        logger.debug("{}: Initialising device IAS Zone cluster for {}", endpoint.getIeeeAddress(),
                channel.getChannelTypeUID());

        clusterIasZone = (ZclIasZoneCluster) endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        if (clusterIasZone == null) {
            logger.error("{}: Error opening IAS zone cluster", endpoint.getIeeeAddress());
            return false;
        }
        // Add a listener, then request the status
        clusterIasZone.addCommandListener(this);
        clusterIasZone.addAttributeListener(this);

        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device IAS zone cluster", endpoint.getIeeeAddress());

        clusterIasZone.removeCommandListener(this);
        clusterIasZone.removeAttributeListener(this);
    }

    @Override
    public void handleRefresh() {
        clusterIasZone.getZoneStatus(0);
    }

    protected boolean supportsIasChannel(ZigBeeEndpoint endpoint, ZoneTypeEnum requiredZoneType) {
        if (!hasIasZoneInputCluster(endpoint)) {
            return false;
        }

        ZclIasZoneCluster cluster = (ZclIasZoneCluster) endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        Integer zoneTypeId = null;
        for (int retry = 0; retry < 3; retry++) {
            zoneTypeId = cluster.getZoneType(Long.MAX_VALUE);
            if (zoneTypeId != null) {
                break;
            }
        }
        if (zoneTypeId == null) {
            logger.debug("{}: Did not get IAS zone type", endpoint.getIeeeAddress());
            return false;
        }
        ZoneTypeEnum zoneType = ZoneTypeEnum.getByValue(zoneTypeId);
        logger.debug("{}: IAS zone type {}", endpoint.getIeeeAddress(), zoneType);
        return zoneType == requiredZoneType;
    }

    protected boolean hasIasZoneInputCluster(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID) == null) {
            logger.trace("{}: IAS zone cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        logger.debug("{}: ZigBee command report {}", endpoint.getIeeeAddress(), command);
        if (command instanceof ZoneStatusChangeNotificationCommand) {
            ZoneStatusChangeNotificationCommand zoneStatus = (ZoneStatusChangeNotificationCommand) command;
            updateChannelState(zoneStatus.getZoneStatus());

            clusterIasZone.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }

        return false;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getClusterType() == ZclClusterType.IAS_ZONE
                && attribute.getId() == ZclIasZoneCluster.ATTR_ZONESTATUS) {
            updateChannelState((Integer) val);
        }
    }

    private void updateChannelState(Integer state) {
        switch (channel.getAcceptedItemType()) {
            case "Switch":
                updateChannelState(((state & bitTest) != 0) ? OnOffType.ON : OnOffType.OFF);
                break;
            case "Contact":
                updateChannelState(((state & bitTest) != 0) ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                break;
            default:
                logger.warn("{}: Unsupported item type {}", endpoint.getIeeeAddress(), channel.getAcceptedItemType());
                break;
        }
    }
}
