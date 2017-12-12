/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneStatusChangeNotificationCommand;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;

/**
 * Converter for the IAS motion sensor.
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConverterIasContactPortal1 extends ZigBeeBaseChannelConverter implements ZclCommandListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterIasContactPortal1.class);

    private ZclIasZoneCluster clusterIasZone;

    @Override
    public boolean initializeConverter() {
        logger.debug("{}: Initialising device IAS Zone cluster", endpoint.getIeeeAddress());

        clusterIasZone = (ZclIasZoneCluster) endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        if (clusterIasZone == null) {
            logger.error("{}: Error opening ias zone cluster", endpoint.getIeeeAddress());
            return false;
        }

        clusterIasZone.bind();

        // Add a listener, then request the status
        clusterIasZone.addCommandListener(this);
        clusterIasZone.getZoneStatus(0);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        ZclAttribute attribute = clusterIasZone.getAttribute(ZclIasZoneCluster.ATTR_ZONESTATUS);
        clusterIasZone.setReporting(attribute, 3, 600);
        return true;
    }

    @Override
    public void disposeConverter() {
        logger.debug("{}: Closing device IAS cluster [contact]", endpoint.getIeeeAddress());

        clusterIasZone.removeCommandListener(this);
    }

    @Override
    public void handleRefresh() {
        clusterIasZone.getZoneStatus(0);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID) == null) {
            return null;
        }

        ZclIasZoneCluster cluster = (ZclIasZoneCluster) endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        if (cluster == null) {
            logger.error("{}: Error opening ias zone cluster", endpoint.getIeeeAddress());
            return null;
        }

        Integer zoneTypeId = cluster.getZoneType(Integer.MAX_VALUE);
        ZoneTypeEnum zoneType = ZoneTypeEnum.getByValue(zoneTypeId);
        if (zoneType != ZoneTypeEnum.CONTACT_SWITCH) {
            return null;
        }

        return createChannel(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_IAS_CONTACT_PORTAL1,
                ZigBeeBindingConstants.ITEM_TYPE_SWITCH, "Contact Portal 1");
    }

    @Override
    public void commandReceived(ZclCommand command) {
        if (command instanceof ZoneStatusChangeNotificationCommand) {
            ZoneStatusChangeNotificationCommand zoneStatus = (ZoneStatusChangeNotificationCommand) command;
            OnOffType state = ((zoneStatus.getZoneStatus() & 0x01) != 0) ? OnOffType.ON : OnOffType.OFF;
            updateChannelState(state);
        }
    }
}
