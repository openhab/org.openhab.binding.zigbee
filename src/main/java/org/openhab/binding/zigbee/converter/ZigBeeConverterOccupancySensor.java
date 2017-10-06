package org.openhab.binding.zigbee.converter;

import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeDevice;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOccupancySensingCluster;

public abstract class ZigBeeConverterOccupancySensor extends ZigBeeChannelConverter implements ZclAttributeListener {
    private static Logger logger = LoggerFactory.getLogger(ZigBeeConverterOccupancySensor.class);

    private StringType movement = new StringType("Movement detected");
    private StringType noMovement = new StringType("No Movement detected");

    private ZclOccupancySensingCluster clusterOccupancy;
    private ZclBasicCluster basicCluster;

    private String channelLabel;
    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }
        logger.debug("{}: Initialising device on/off cluster", device.getIeeeAddress());

        clusterOccupancy = (ZclOccupancySensingCluster) device.getCluster(ZclOccupancySensingCluster.CLUSTER_ID);

        if (clusterOccupancy == null) {
            logger.error("{}: Error opening device on/off controls", device.getIeeeAddress());
            return;
        }

        // Add a listener, then request the status
        clusterOccupancy.addAttributeListener(this);
        clusterOccupancy.getOccupancy(0);

        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        try {
            clusterOccupancy.setOccupancyReporting(1, 600).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (initialised == false) {
            return;
        }

        logger.debug("{}: Closing device on/off cluster", device.getIeeeAddress());

        if (clusterOccupancy != null) {
            clusterOccupancy.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        if (initialised == false) {
            return;
        }

        int value = Integer.lowestOneBit(clusterOccupancy.getOccupancy(0));
        if (value == 1) {
            updateChannelState(movement);
        } else {
            updateChannelState(noMovement);
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeDevice device) {
        if (device.getCluster(ZclOccupancySensingCluster.CLUSTER_ID) == null) {
            return null;
        }
        basicCluster = (ZclBasicCluster) device.getCluster(ZclBasicCluster.CLUSTER_ID);
        if (basicCluster == null) {
            logger.error("{}: Error opening device baisic controls", device.getIeeeAddress());
        }

        // Get cluster location descriptor for channel label
        if (basicCluster != null) {
            channelLabel = basicCluster.getLocationDescription(0);
        } else {
            channelLabel = "Sensor";
        }
        return createChannel(device, thingUID, ZigBeeBindingConstants.CHANNEL_SENSOR_OCCUPANCY,
                ZigBeeBindingConstants.ITEM_TYPE_SENSOR, channelLabel);
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute) {
        logger.debug("{}: ZigBee attribute reports {}", device.getIeeeAddress(), attribute);
        if (attribute.getId() == ZclOccupancySensingCluster.ATTR_OCCUPANCY) {
            Integer value = (Integer) attribute.getLastValue();
            if (value != null && Integer.lowestOneBit(value) == 1) {
                updateChannelState(movement);
            } else {
                updateChannelState(noMovement);
            }
        }
    }
}