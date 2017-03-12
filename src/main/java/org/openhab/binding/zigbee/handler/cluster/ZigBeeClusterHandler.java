/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler.cluster;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.smarthome.config.core.ConfigDescription;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeDevice;
import com.zsmartsystems.zigbee.ZigBeeDeviceAddress;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * ZigBeeClusterConverter class. Base class for all converters that convert between ZigBee clusters and openHAB
 * channels.
 *
 * @author Chris Jackson
 */
public abstract class ZigBeeClusterHandler {
    private static Logger logger = LoggerFactory.getLogger(ZigBeeClusterHandler.class);

    protected ZigBeeThingHandler thing = null;
    // protected ZigBeeThingChannel channel = null;
    protected ZigBeeCoordinatorHandler coordinator = null;

    protected ChannelUID channelUID = null;
    protected ZigBeeDevice device = null;

    private static Map<Integer, Class<? extends ZigBeeClusterHandler>> clusterMap = null;

    /**
     * Constructor. Creates a new instance of the {@link ZWaveCommandClassConverter} class.
     *
     */
    public ZigBeeClusterHandler() {
        super();
    }

    /**
     * Creates the converter handler
     *
     * @param thing
     * @param channelUID
     * @param coordinator
     * @param address
     * @return true if the handler was created successfully - false otherwise
     */
    public boolean createConverter(ZigBeeThingHandler thing, ChannelUID channelUID,
            ZigBeeCoordinatorHandler coordinator, String address) {
        this.device = coordinator.getDevice(new ZigBeeDeviceAddress(address));
        if (this.device == null) {
            return false;
        }
        this.thing = thing;
        this.channelUID = channelUID;
        this.coordinator = coordinator;

        return true;
    }

    public abstract void initializeConverter();

    public void disposeConverter() {
    }

    /**
     * Execute refresh method. This method is called every time a binding item is refreshed and the corresponding node
     * should be sent a message.
     *
     * @param channel the {@link ZigBeeThingChannel}
     */
    public void handleRefresh() {
    }

    /**
     * Receives a command from openHAB and translates it to an operation on the Z-Wave network.
     *
     * @param channel the {@link ZigBeeThingChannel}
     * @param command the {@link Command} to send
     */
    public Runnable handleCommand(final Command command) {
        return null;
    }

    public abstract List<Channel> getChannels(ThingUID thingUID, ZigBeeDevice device);

    /**
     *
     * @param clusterId
     * @return
     */
    public static ZigBeeClusterHandler getConverter(int clusterId) {
        if (clusterMap == null) {
            clusterMap = new HashMap<Integer, Class<? extends ZigBeeClusterHandler>>();

            // Add all the handlers into the map...
            clusterMap.put(ZclClusterType.ON_OFF.getId(), ZigBeeOnOffClusterHandler.class);
            clusterMap.put(ZclClusterType.LEVEL_CONTROL.getId(), ZigBeeLevelClusterHandler.class);
            clusterMap.put(ZclClusterType.COLOR_CONTROL.getId(), ZigBeeColorClusterHandler.class);
            // clusterMap.put(ZigBeeApiConstants.CLUSTER_ID_RELATIVE_HUMIDITY_MEASUREMENT,
            // ZigBeeRelativeHumidityMeasurementClusterHandler.class);
            // clusterMap.put(ZigBeeApiConstants.CLUSTER_ID_TEMPERATURE_MEASUREMENT,
            // ZigBeeTemperatureMeasurementClusterHandler.class);
        }

        Constructor<? extends ZigBeeClusterHandler> constructor;
        try {
            if (clusterMap.get(clusterId) == null) {
                logger.debug("Cluster converter for cluster {} is not implemented!", clusterId);
                return null;
            }
            constructor = clusterMap.get(clusterId).getConstructor();
            return constructor.newInstance();
        } catch (Exception e) {
            // logger.error("Command processor error");
        }

        return null;
    }

    protected void updateChannelState(State state) {
        thing.setChannelState(channelUID, state);
    }

    /**
     * Gets the configuration descriptions required for this cluster
     *
     * @return {@link ConfigDescription} null if no config is provided
     */
    public ConfigDescription getConfigDescription() {
        return null;
    }

    protected Channel createChannel(ZigBeeDevice device, ThingUID thingUID, String channelType, String itemType,
            String label) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(ZigBeeBindingConstants.CHANNEL_PROPERTY_ADDRESS, device.getDeviceAddress().toString());
        properties.put(ZigBeeBindingConstants.CHANNEL_PROPERTY_CLUSTER, Integer.toString(getClusterId()));
        ChannelTypeUID channelTypeUID = new ChannelTypeUID(ZigBeeBindingConstants.BINDING_ID, channelType);

        return ChannelBuilder
                .create(new ChannelUID(thingUID,
                        device.getIeeeAddress() + "_" + device.getEndpoint() + "_" + channelType), itemType)
                .withType(channelTypeUID).withLabel(label).withProperties(properties).build();
    }

    public abstract int getClusterId();
}
