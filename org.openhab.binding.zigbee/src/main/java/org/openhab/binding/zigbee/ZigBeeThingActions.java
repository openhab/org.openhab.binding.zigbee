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
package org.openhab.binding.zigbee;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.binding.ThingActions;
import org.eclipse.smarthome.core.thing.binding.ThingActionsScope;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.RuleAction;

import static org.eclipse.jdt.annotation.Checks.requireNonNull;

/**
 * The {@link ZigBeeThingActions} defines actions to be triggered by rules.
 * It allows the specification of parameters to commands which is not possible via normal OH commands.
 *
 * @author Thomas Weißschuh - Initial contribution
 */
@SuppressWarnings("unused")
@ThingActionsScope(name="zigbee")
@NonNullByDefault
public final class ZigBeeThingActions implements ThingActions {
    private @Nullable ZigBeeThingHandler handler;

    @Override
    public void setThingHandler(@Nullable final ThingHandler handler) {
        this.handler = (ZigBeeThingHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "sendCommand")
    public void sendCommand(
            @ActionInput(name = "channelId", required = true) final String channelId,
            @ActionInput(name = "command", required = true) final Command command,
            @ActionInput(name = "params") @Nullable final ZigBeeCommandParameters params
    ) {
        handleCommand(getChannel(channelId), command, params != null ? params : ZigBeeCommandParameters.empty());
    }

    private void handleCommand(final ChannelUID channel, final Command command, final ZigBeeCommandParameters params) {
        requireNonNull(handler).handleCommand(channel, command, params);
    }

    private ChannelUID getChannel(final String channelId) {
        return new ChannelUID(requireNonNull(handler).getThing().getUID(), channelId);
    }
}
