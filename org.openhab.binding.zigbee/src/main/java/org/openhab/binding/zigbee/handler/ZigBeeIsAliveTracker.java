/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service which starts timeout tasks to set a Thing to OFFLINE if it doesn't reset the timer before it runs out
 */
@Component(immediate = true, service = ZigBeeIsAliveTracker.class)
public class ZigBeeIsAliveTracker {

    private final Logger logger = LoggerFactory.getLogger(ZigBeeIsAliveTracker.class);

    private Map<ZigBeeThingHandler, Integer> handlerIntervalMapping = new ConcurrentHashMap<>();
    private Map<ZigBeeThingHandler, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    protected final ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool("ZigBeeIsAliveTracker");

    /**
     * Adds a mapping from {@link ZigBeeThingHandler} to the interval in which it should have communicated
     *
     * @param zigBeeThingHandler the {@link ZigBeeThingHandler}
     * @param expectedUpdateInterval the interval in which the device should have communicated with us
     */
    public void addHandler(ZigBeeThingHandler zigBeeThingHandler, int expectedUpdateInterval) {
        logger.debug("Add IsAlive Tracker for thingUID={}", zigBeeThingHandler.getThing().getUID());
        handlerIntervalMapping.put(zigBeeThingHandler, expectedUpdateInterval);
        resetTimer(zigBeeThingHandler);
    }

    /**
     * Removes a {@link ZigBeeThingHandler} from the map so it is not tracked anymore
     *
     * @param zigBeeThingHandler the {@link ZigBeeThingHandler}
     */
    public void removeHandler(ZigBeeThingHandler zigBeeThingHandler) {
        logger.debug("Remove IsAlive Tracker for thingUID={}", zigBeeThingHandler.getThing().getUID());
        cancelTask(zigBeeThingHandler);
        handlerIntervalMapping.remove(zigBeeThingHandler);
    }

    /**
     * Reset the timer for a {@link ZigBeeThingHandler}, i.e. expressing that some communication has just occurred
     *
     * @param zigBeeThingHandler the {@link ZigBeeThingHandler}
     */
    public synchronized void resetTimer(ZigBeeThingHandler zigBeeThingHandler) {
        logger.debug("Reset timeout for handler with thingUID={}", zigBeeThingHandler.getThing().getUID());
        cancelTask(zigBeeThingHandler);
        scheduleTask(zigBeeThingHandler);
    }

    private void scheduleTask(ZigBeeThingHandler handler) {
        ScheduledFuture<?> existingTask = scheduledTasks.get(handler);
        if (existingTask == null && handlerIntervalMapping.containsKey(handler)) {
            int interval = handlerIntervalMapping.get(handler);
            logger.debug("Scheduling timeout task for thingUID={} in {} seconds",
                    handler.getThing().getUID().getAsString(), interval);
            ScheduledFuture<?> task = scheduler.schedule(() -> {
                logger.debug("Timeout has been reached for thingUID={}", handler.getThing().getUID().getAsString());
                handler.aliveTimeoutReached();
                scheduledTasks.remove(handler);
            }, interval, TimeUnit.SECONDS);

            scheduledTasks.put(handler, task);
        }
    }

    private void cancelTask(ZigBeeThingHandler handler) {
        ScheduledFuture<?> task = scheduledTasks.get(handler);
        if (task != null) {
            logger.debug("Canceling timeout task for thingUID={}", handler.getThing().getUID().getAsString());
            task.cancel(true);
            scheduledTasks.remove(handler);
        }
    }
}
