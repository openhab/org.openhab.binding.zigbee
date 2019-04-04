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
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
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
    public boolean initializeConverter() {
        logger.debug("{}: Initialising device IAS Zone cluster for {}", endpoint.getIeeeAddress(),
                channel.getChannelTypeUID());

        clusterIasZone = (ZclIasZoneCluster) endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        if (clusterIasZone == null) {
            logger.error("{}: Error opening IAS zone cluster", endpoint.getIeeeAddress());
            return false;
        }

        bind(clusterIasZone);

        // Add a listener, then request the status
        clusterIasZone.addCommandListener(this);
        clusterIasZone.addAttributeListener(this);

        // Configure reporting - no faster than once per second - no slower than 2 hours.
        ZclAttribute attribute = clusterIasZone.getAttribute(ZclIasZoneCluster.ATTR_ZONESTATUS);
        try {
            CommandResult reportingResponse = clusterIasZone.setReporting(attribute, 3, REPORTING_PERIOD_DEFAULT_MAX)
                    .get();
            handleReportingResponse(reportingResponse, POLLING_PERIOD_DEFAULT, REPORTING_PERIOD_DEFAULT_MAX);
        } catch (InterruptedException | ExecutionException e) {
            logger.debug("{}: Exception configuring ias zone status reporting", endpoint.getIeeeAddress(), e);
        }
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
    public void commandReceived(ZclCommand command) {
        logger.debug("{}: ZigBee command report {}", endpoint.getIeeeAddress(), command);
        if (command instanceof ZoneStatusChangeNotificationCommand) {
            ZoneStatusChangeNotificationCommand zoneStatus = (ZoneStatusChangeNotificationCommand) command;
            updateChannelState(zoneStatus.getZoneStatus());
        }
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), attribute);
        if (attribute.getCluster() == ZclClusterType.IAS_ZONE
                && attribute.getId() == ZclIasZoneCluster.ATTR_ZONESTATUS) {
            updateChannelState((Integer) attribute.getLastValue());
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
