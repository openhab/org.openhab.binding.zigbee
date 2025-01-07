/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

import java.math.BigDecimal;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.MoveToColorTemperatureCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Channel converter for absolute color temperature based on {@link ZigBeeConverterColorTemperature}, that converts
 * between the color control cluster and a QuantityType<Temperature> channel.
 *
 * @author Andrew Fiddian-Green - Initial Contribution
 */
public class ZigBeeConverterColorTempAbsolute extends ZigBeeConverterColorTemperature {

    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterColorTempAbsolute.class);

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        if (super.initializeConverter(thing)) {
            stateDescription = StateDescriptionFragmentBuilder.create().withMinimum(BigDecimal.valueOf(kelvinMin))
                    .withMaximum(BigDecimal.valueOf(kelvinMax)).withStep(BigDecimal.valueOf(100)).withPattern("%.0f K")
                    .build().toStateDescription();
            return true;
        }
        return false;
    }

    @Override
    public void handleCommand(final Command command) {
        if (command instanceof QuantityType<?> quantity) {
            QuantityType<?> mired = quantity.toInvertibleUnit(Units.MIRED);
            if (mired != null) {
                MoveToColorTemperatureCommand zclCommand = new MoveToColorTemperatureCommand(mired.intValue(), 10);
                monitorCommandResponse(command, clusterColorControl.sendCommand(zclCommand));
            }
            return;
        }
        super.handleCommand(command);
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        return super.getChannel(thingUID, endpoint) == null ? null
                : ChannelBuilder
                        .create(createChannelUID(thingUID, endpoint,
                                ZigBeeBindingConstants.CHANNEL_NAME_COLOR_TEMP_ABSOLUTE),
                                ZigBeeBindingConstants.ITEM_TYPE_NUMBER_TEMPERATURE)
                        .withType(ZigBeeBindingConstants.CHANNEL_COLOR_TEMP_ABSOLUTE)
                        .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_COLOR_TEMPERATURE)
                        .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        if (attribute.getClusterType() == ZclClusterType.COLOR_CONTROL
                && attribute.getId() == ZclColorControlCluster.ATTR_COLORTEMPERATURE) {
            logger.debug("{}: ZigBee attribute reports {} on endpoint {}", endpoint.getIeeeAddress(), attribute,
                    endpoint.getEndpointId());
            if (val instanceof Integer mired) {
                updateChannelState(QuantityType.valueOf(mired, Units.MIRED));
            }
            return;
        }
        super.attributeUpdated(attribute, val);
    }
}
