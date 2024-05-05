package org.openhab.binding.zigbee.slzb06.internal.api;

/**
 * Sensor information response from SLZB-06
 *
 * @author Chris Jackson
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
