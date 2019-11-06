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
package org.openhab.binding.zigbee.internal.converter;

import java.math.BigDecimal;

import com.zsmartsystems.zigbee.zcl.clusters.ZclTemperatureMeasurementCluster;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.State;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_LABEL_TEMPERATURE_VALUE;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_NAME_TEMPERATURE_VALUE;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_TEMPERATURE_VALUE;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.ITEM_TYPE_NUMBER_TEMPERATURE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converter for the temperature channel
 *
 * @author Chris Jackson - Initial Contribution
 */
public class ZigBeeConverterTemperature extends ZigBeeReportableAttributeConverter {
    private static Logger logger = LoggerFactory.getLogger(ZigBeeConverterTemperature.class);

    private static final boolean IS_ANALOGUE = true;
    private static final String NAME_FOR_LOGGING = "Temperature measurement";
    private static final int CLUSTER_ID = ZclTemperatureMeasurementCluster.CLUSTER_ID;
    private static final int ATTRIBUTE_ID = ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE;
    private static final String CHANNEL_NAME = CHANNEL_NAME_TEMPERATURE_VALUE;
    private static final String LABEL = CHANNEL_LABEL_TEMPERATURE_VALUE;
    private static final String ITEM_TYPE = ITEM_TYPE_NUMBER_TEMPERATURE;
    private static final ChannelTypeUID CHANNEL_TYPE_UID = CHANNEL_TEMPERATURE_VALUE;
    protected static BigDecimal CHANGE_DEFAULT = new BigDecimal(50);
    protected static BigDecimal CHANGE_MIN = new BigDecimal(1);
    protected static BigDecimal CHANGE_MAX = new BigDecimal(20000);

    public ZigBeeConverterTemperature() {
        super(ATTRIBUTE_ID, CLUSTER_ID, CHANNEL_NAME, LABEL, ITEM_TYPE, CHANNEL_TYPE_UID, IS_ANALOGUE, CHANGE_DEFAULT,
                CHANGE_MIN, CHANGE_MAX, logger, NAME_FOR_LOGGING);
    }

    @Override
    public State convertValueToState(Integer val) {
        return valueToTemperature(val);
    }
}
