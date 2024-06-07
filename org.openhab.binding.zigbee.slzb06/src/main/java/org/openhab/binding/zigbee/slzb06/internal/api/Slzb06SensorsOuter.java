/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.slzb06.internal.api;

/**
 * Sensor information response from SLZB-06
 *
 * @author Chris Jackson - Initial contribution
 *
 */
public class Slzb06SensorsOuter extends Slzb06Response {
    Slzb06Sensors Sensors;

    public class Slzb06Sensors {
        public Double esp32_temp;
        public Double zb_temp;
        public Long uptime;
        public Long socket_uptime;
        public Boolean ethernet;
        public Boolean wifi_connected;
        public Boolean disable_leds;
        public Boolean night_mode;
    }
}
