package org.openhab.binding.zigbee.tuya.handler;

import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.handler.ZigBeeBaseThingHandler;
import org.openhab.binding.zigbee.handler.ZigBeeIsAliveTracker;
import org.openhab.core.thing.Thing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.ZigBeeNode;

/**
 * Handler for Tuya AM25 blinds
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class TuyaBlindsThingHandler extends ZigBeeBaseThingHandler {
    private Logger logger = LoggerFactory.getLogger(TuyaBlindsThingHandler.class);

    public TuyaBlindsThingHandler(Thing zigbeeDevice, ZigBeeChannelConverterFactory channelFactory,
            ZigBeeIsAliveTracker zigbeeIsAliveTracker) {
        super(zigbeeDevice, channelFactory, zigbeeIsAliveTracker);
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void doNodeInitialisation(ZigBeeNode node) {
        ZigBeeEndpoint endpoint = node.getEndpoint(1);
        if (endpoint == null) {
            logger.error("{}: Tuya blinds handler couldn't find endpoint 1", node.getIeeeAddress());
            return;
        }
    }

}
