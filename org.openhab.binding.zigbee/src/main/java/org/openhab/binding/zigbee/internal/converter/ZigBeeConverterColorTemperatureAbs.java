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
package org.openhab.binding.zigbee.internal.converter;

import java.math.BigDecimal;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.MoveToColorTemperatureCommand;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

import tech.units.indriya.unit.UnitDimension;

/**
 * Channel converter for color temperature, converting between the color control cluster and a Kelvin
 * QuantityType<Temperature> channel.
 *
 * @author Andrew Fiddian-Green - Initial Contribution
 */
public class ZigBeeConverterColorTemperatureAbs extends ZigBeeConverterColorTemperature {

    private Logger logger = LoggerFactory.getLogger(ZigBeeConverterColorTemperatureAbs.class);

    private boolean updateStateDescriptionDone;

    @Override
    public void initialize(Channel channel, ZigBeeCoordinatorHandler coordinator, IeeeAddress address, int endpointId) {
        super.initialize(channel, coordinator, address, endpointId);
        updateStateDescriptionDone = false;
    }

    @Override
    public void handleCommand(final Command command) {
        if (command instanceof QuantityType<?> quantity && UnitDimension.TEMPERATURE == quantity.getDimension()) {
            QuantityType<?> kelvin = quantity.toInvertibleUnit(Units.KELVIN);
            if (kelvin != null) {
                logger.debug("handleCommand() channel {} to {}", channelUID, kelvin);
                MoveToColorTemperatureCommand zclCommand = new MoveToColorTemperatureCommand(
                        1000000 / kelvin.intValue(), 10);
                monitorCommandResponse(command, clusterColorControl.sendCommand(zclCommand));
            }
        } else {
            super.handleCommand(command);
        }
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        return super.getChannel(thingUID, endpoint) == null ? null
                : ChannelBuilder
                        .create(createChannelUID(thingUID, endpoint,
                                ZigBeeBindingConstants.CHANNEL_NAME_COLOR_TEMPERATURE_ABS),
                                ZigBeeBindingConstants.ITEM_TYPE_NUMBER_TEMPERATURE)
                        .withType(ZigBeeBindingConstants.CHANNEL_COLOR_TEMPERATURE_ABS)
                        .withLabel(ZigBeeBindingConstants.CHANNEL_LABEL_COLOR_TEMPERATURE)
                        .withProperties(createProperties(endpoint)).build();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        if (attribute.getClusterType() == ZclClusterType.COLOR_CONTROL
                && attribute.getId() == ZclColorControlCluster.ATTR_COLORTEMPERATURE && val instanceof Integer mired) {
            updateStateDescription();
            updateChannelState(QuantityType.valueOf(1000000 / mired, Units.KELVIN));
        } else {
            super.attributeUpdated(attribute, val);
        }
    }

    /**
     * Set the state description minimum and maximum values and pattern in Kelvin for the given channel UID
     */
    private void updateStateDescription() {
        if (!updateStateDescriptionDone) {
            updateStateDescriptionDone = true;
            logger.debug("updateStateDescription() {} state min/max = {}/{} Kelvin", channelUID, kelvinMin, kelvinMax);
            thing.putStateDescription(channelUID,
                    StateDescriptionFragmentBuilder.create().withMinimum(BigDecimal.valueOf(kelvinMin))
                            .withMaximum(BigDecimal.valueOf(kelvinMax)).withStep(BigDecimal.valueOf(100))
                            .withPattern("%.0f K").build().toStateDescription());
        }
    }
}
