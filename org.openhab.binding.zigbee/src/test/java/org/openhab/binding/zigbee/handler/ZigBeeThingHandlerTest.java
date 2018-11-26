/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

/**
 * Test of the ZigBeeThingHandler
 *
 * @author Chris Jackson - Initial contribution
 *
 */
public class ZigBeeThingHandlerTest {
    private List<Integer> processClusterList(Collection<Integer> initialClusters, String newClusters)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
            SecurityException {
        Method privateMethod;

        ZigBeeThingHandler handler = new ZigBeeThingHandler(null);

        privateMethod = ZigBeeThingHandler.class.getDeclaredMethod("processClusterList", Collection.class,
                String.class);
        privateMethod.setAccessible(true);

        return (List<Integer>) privateMethod.invoke(handler, initialClusters, newClusters);
    }

    @Test
    public void testProcessClusterList() throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, SecurityException {
        List<Integer> clusters = new ArrayList<>();

        clusters = processClusterList(clusters, null);
        assertEquals(0, clusters.size());

        clusters = processClusterList(clusters, "");
        assertEquals(0, clusters.size());

        clusters = processClusterList(clusters, ",");
        assertEquals(0, clusters.size());

        clusters = processClusterList(clusters, "123,456");
        assertEquals(2, clusters.size());
        assertTrue(clusters.contains(123));
        assertTrue(clusters.contains(456));

        clusters = processClusterList(clusters, "123,456");
        assertEquals(0, clusters.size());

        clusters = processClusterList(clusters, "123,456");
        assertEquals(2, clusters.size());
        assertTrue(clusters.contains(123));
        assertTrue(clusters.contains(456));

        clusters = processClusterList(clusters, "321,654");
        assertEquals(4, clusters.size());
        assertTrue(clusters.contains(123));
        assertTrue(clusters.contains(456));
        assertTrue(clusters.contains(321));
        assertTrue(clusters.contains(654));
    }
}
