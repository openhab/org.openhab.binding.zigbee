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
package org.openhab.binding.zigbee.internal.converter.command;

import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclFieldDeserializer;
import com.zsmartsystems.zigbee.zcl.ZclFieldSerializer;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclCommandDirection;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

public class TuyaButtonPressCommand extends ZclCommand {
    /**
     * The command ID.
     */
    public static int COMMAND_ID = 0xFD;

    /**
     * Type of button press.
     * <p>
     * 0 = short press
     * 1 = double press
     * 2 = long press
     */
    private Integer pressType;

    /**
     * Default constructor.
     *
     */
    public TuyaButtonPressCommand() {
        clusterId = ZclOnOffCluster.CLUSTER_ID;
        commandId = COMMAND_ID;
        genericCommand = false;
        commandDirection = ZclCommandDirection.CLIENT_TO_SERVER;
    }

    public TuyaButtonPressCommand(
        Integer pressType)
    {
        this();
        this.pressType = pressType;
    }

    public Integer getPressType()
    {
        return pressType;
    }

    @Override
    public void serialize(ZclFieldSerializer serializer) {
        serializer.serialize(pressType, ZclDataType.UNSIGNED_8_BIT_INTEGER);
    }

    @Override
    public void deserialize(final ZclFieldDeserializer deserializer) {
        pressType = (Integer) deserializer.deserialize(ZclDataType.UNSIGNED_8_BIT_INTEGER);
    }
    
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(113);
        builder.append(this.getClass().getSimpleName());
        builder.append(" [");
        builder.append(super.toString());
        builder.append(", pressType=");
        builder.append(pressType);
        builder.append(']');
        return builder.toString();
    }
}
