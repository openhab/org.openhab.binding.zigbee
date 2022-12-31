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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import com.zsmartsystems.zigbee.security.ZigBeeKey;

/**
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeCoordinatorHandlerTest {

    @Test
    public void testInstallCode() throws NoSuchFieldException, SecurityException, IllegalArgumentException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        ZigBeeCoordinatorHandler handler = Mockito.mock(ZigBeeCoordinatorHandler.class, Mockito.CALLS_REAL_METHODS);

        ZigBeeNetworkManager networkManager = Mockito.mock(ZigBeeNetworkManager.class);
        ArgumentCaptor<ZigBeeKey> keyCapture = ArgumentCaptor.forClass(ZigBeeKey.class);
        Mockito.when(networkManager.setZigBeeInstallKey(keyCapture.capture())).thenReturn(ZigBeeStatus.SUCCESS);

        Field fieldNetworkManager = ZigBeeCoordinatorHandler.class.getDeclaredField("networkManager");
        fieldNetworkManager.setAccessible(true);
        fieldNetworkManager.set(handler, networkManager);

        Method privateMethod = ZigBeeCoordinatorHandler.class.getDeclaredMethod("addInstallCode", String.class);
        privateMethod.setAccessible(true);

        privateMethod.invoke(handler, "1122334455667788:83FE-D340-7A93-9738-C552");
        assertEquals(new IeeeAddress("1122334455667788"), keyCapture.getValue().getAddress());
        assertEquals(new ZigBeeKey("A833A77434F3BFBD7A7AB97942149287"), keyCapture.getValue());
    }

}
