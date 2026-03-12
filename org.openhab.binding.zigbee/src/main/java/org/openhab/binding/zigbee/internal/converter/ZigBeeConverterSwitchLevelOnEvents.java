/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This channel supports triggering events related to the on button of the ZigBeeConverterSwitchLevel
 *
 * @author Jörg Sautter - Initial Contribution
 *
 */
public class ZigBeeConverterSwitchLevelOnEvents extends ZigBeeBaseChannelConverter {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeConverterSwitchLevelOnEvents.class);

    // The number of milliseconds after the last action is performed to raise the event
    private static final int EVENT_TIMEFRAME = 800;

    private record Event(int count, ScheduledFuture<?> timer) { }
    private ScheduledExecutorService eventScheduler;
    private Event lastEvent = new Event(-1, null);

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Stream.of(ZclOnOffCluster.CLUSTER_ID, ZclLevelControlCluster.CLUSTER_ID).collect(Collectors.toSet());
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Stream.of(ZclOnOffCluster.CLUSTER_ID, ZclLevelControlCluster.CLUSTER_ID).collect(Collectors.toSet());
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        eventScheduler = Executors.newSingleThreadScheduledExecutor();
        return true;
    }

    @Override
    public boolean initializeDevice() {
        return true;
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID) == null
                && endpoint.getOutputCluster(ZclLevelControlCluster.CLUSTER_ID) == null) {
            logger.trace("{}: Level control cluster not found", endpoint.getIeeeAddress());
            return null;
        }

        return ChannelBuilder
                .create(createChannelUID(thingUID, endpoint, ZigBeeBindingConstants.CHANNEL_NAME_SWITCH_LEVEL_ON_EVENTS))
                .withKind(ChannelKind.TRIGGER)
                .withType(ZigBeeBindingConstants.CHANNEL_SWITCH_LEVEL_ON_EVENTS)
                .withLabel(getDeviceTypeLabel(endpoint) + ": " + ZigBeeBindingConstants.CHANNEL_LABEL_SWITCH_LEVEL_ON_EVENTS)
                .withProperties(createProperties(endpoint))
                .build();
    }

    /**
     * Plan the event to the trigger channel within the thing, to trigger the 
     * event if there is no other action within the next EVENT_TIMEFRAME.
     */
    public void planChannelEvent() {
        logger.debug("{}: Plan on event to channel {}", endpoint.getIeeeAddress(), channelUID);

        int lastCount;

        // is there a button 
        if (lastEvent.timer != null && lastEvent.timer.cancel(false)) {
            lastCount = lastEvent.count;
        } else {
            lastCount = 0;
        }

        String plan = switch(lastCount) {
            case 0 -> CommonTriggerEvents.SHORT_PRESSED;
            case 1 -> CommonTriggerEvents.DOUBLE_PRESSED;
            default -> null; // give the change to cancle raising the event
        };

        lastEvent = new Event(lastCount + 1, eventScheduler.schedule(() -> {
            // ignore the button has been pressed more than three times
            if (plan != null) {
                triggerEventChannel(plan);
            }
        }, EVENT_TIMEFRAME, TimeUnit.MILLISECONDS));
    }

    @Override
    public void disposeConverter() {
        eventScheduler.shutdownNow();
    }
}
