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

import com.zsmartsystems.zigbee.zcl.clusters.ZclIlluminanceMeasurementCluster;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.State;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_ILLUMINANCE_VALUE;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_LABEL_ILLUMINANCE_VALUE;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_NAME_ILLUMINANCE_VALUE;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.ITEM_TYPE_NUMBER;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converter for the illuminance channel
 *
 * @author Chris Jackson - Initial Contribution
 */
public class ZigBeeConverterIlluminance extends ZigBeeReportableAttributeConverter {

    private static final boolean IS_ANALOGUE = true;
    private static final String NAME_FOR_LOGGING = "Illuminance measurement";
    private static final Logger logger = LoggerFactory.getLogger("illuminasty");
    private static final int CLUSTER_ID = ZclIlluminanceMeasurementCluster.CLUSTER_ID;
    private static final int ATTRIBUTE_ID = ATTR_MEASUREDVALUE;
    private static final String CHANNEL_NAME = CHANNEL_NAME_ILLUMINANCE_VALUE;
    private static final String LABEL = CHANNEL_LABEL_ILLUMINANCE_VALUE;
    private static final String ITEM_TYPE = ITEM_TYPE_NUMBER;
    private static final ChannelTypeUID CHANNEL_TYPE_UID = CHANNEL_ILLUMINANCE_VALUE;
    protected static BigDecimal CHANGE_DEFAULT = new BigDecimal(5000);
    protected static BigDecimal CHANGE_MIN = new BigDecimal(10);
    protected static BigDecimal CHANGE_MAX = new BigDecimal(20000);

    public ZigBeeConverterIlluminance() {
        super(ATTRIBUTE_ID, CLUSTER_ID, CHANNEL_NAME, LABEL, ITEM_TYPE, CHANNEL_TYPE_UID, IS_ANALOGUE, CHANGE_DEFAULT,
                CHANGE_MIN, CHANGE_MAX, logger, NAME_FOR_LOGGING);
    }

    @Override
    public State convertValueToState(Integer val) {
        logger.debug("converting illuminace from {}", val);
        return new DecimalType(Math.pow(10.0, val / 10000.0) - 1);
    }
}
