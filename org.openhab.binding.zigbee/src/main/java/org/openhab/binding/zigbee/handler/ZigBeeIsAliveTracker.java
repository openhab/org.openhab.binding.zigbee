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
package org.openhab.binding.zigbee.handler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.openhab.core.common.ThreadPoolManager;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service which starts timeout tasks to set a Thing to OFFLINE if it doesn't reset the timer before it runs out.
 * <p>
 * This is a two stage tracker - when the initial timer times out we call aliveTimeoutLastChance to notify the handler
 * the thing is about to be set OFFLINE. Shortly after (eg 30 seconds) if there has still not been an update,
 * aliveTimeoutReached is called to set the thing OFFLINE.
 *
 * @author Stefan Triller - Initial contribution
 * @author Chris Jackson - Added last chance timer
 */
@Component(immediate = true, service = ZigBeeIsAliveTracker.class)
public class ZigBeeIsAliveTracker {
    private static final int LAST_CHANCE_TIMER = 30; // Seconds

    private final Logger logger = LoggerFactory.getLogger(ZigBeeIsAliveTracker.class);

    private Map<ZigBeeThingHandler, Integer> handlerIntervalMapping = new ConcurrentHashMap<>();
    private Map<ZigBeeThingHandler, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private Set<ZigBeeThingHandler> lastChance = new HashSet<>();

    protected final ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool("ZigBeeIsAliveTracker");

    /**
     * Adds a mapping from {@link ZigBeeThingHandler} to the interval in which it should have communicated
     *
     * @param zigBeeThingHandler the {@link ZigBeeThingHandler}
     * @param expectedUpdateInterval the interval in which the device should have communicated with us
     */
    public void addHandler(ZigBeeThingHandler zigBeeThingHandler, int expectedUpdateInterval) {
        logger.debug("IsAlive Tracker added for thingUID={}", zigBeeThingHandler.getThing().getUID());
        handlerIntervalMapping.put(zigBeeThingHandler, expectedUpdateInterval);
        resetTimer(zigBeeThingHandler);
    }

    /**
     * Removes a {@link ZigBeeThingHandler} from the map so it is not tracked anymore
     *
     * @param zigBeeThingHandler the {@link ZigBeeThingHandler}
     */
    public void removeHandler(ZigBeeThingHandler zigBeeThingHandler) {
        logger.debug("IsAlive Tracker removed for thingUID={}", zigBeeThingHandler.getThing().getUID());
        cancelTask(zigBeeThingHandler);
        lastChance.remove(zigBeeThingHandler);
        handlerIntervalMapping.remove(zigBeeThingHandler);
    }

    /**
     * Reset the timer for a {@link ZigBeeThingHandler}, i.e. expressing that some communication has just occurred
     *
     * @param zigBeeThingHandler the {@link ZigBeeThingHandler}
     */
    public synchronized void resetTimer(ZigBeeThingHandler zigBeeThingHandler) {
        logger.debug("IsAlive Tracker reset for handler with thingUID={}", zigBeeThingHandler.getThing().getUID());
        cancelTask(zigBeeThingHandler);
        ScheduledFuture<?> existingTask = scheduledTasks.get(zigBeeThingHandler);
        if (existingTask == null && handlerIntervalMapping.containsKey(zigBeeThingHandler)) {
            int interval = handlerIntervalMapping.get(zigBeeThingHandler);
            logger.debug("IsAlive Tracker scheduled task for thingUID={} in {} seconds",
                    zigBeeThingHandler.getThing().getUID().getAsString(), interval);
            scheduleTask(zigBeeThingHandler, interval);
        }
    }

    private void scheduleTask(ZigBeeThingHandler handler, int interval) {
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            synchronized (lastChance) {
                scheduledTasks.remove(handler);
                if (!lastChance.contains(handler)) {
                    logger.debug("IsAlive Tracker LastChance Timeout has been reached for thingUID={}",
                            handler.getThing().getUID().getAsString());

                    // Notify the thing handler this is its last chance before it's marked OFFLINE
                    lastChance.add(handler);
                    handler.aliveTimeoutLastChance();

                    scheduleTask(handler, LAST_CHANCE_TIMER);
                } else {
                    logger.debug("IsAlive Tracker Timeout has been reached for thingUID={}",
                            handler.getThing().getUID().getAsString());

                    lastChance.remove(handler);
                    handler.aliveTimeoutReached();
                }
            }
        }, interval, TimeUnit.SECONDS);

        scheduledTasks.put(handler, task);
    }

    private void cancelTask(ZigBeeThingHandler handler) {
        lastChance.remove(handler);
        ScheduledFuture<?> task = scheduledTasks.get(handler);
        if (task != null) {
            logger.debug("IsAlive Tracker cancelled task for thingUID={}", handler.getThing().getUID().getAsString());
            task.cancel(true);
            scheduledTasks.remove(handler);
        }
    }
}
