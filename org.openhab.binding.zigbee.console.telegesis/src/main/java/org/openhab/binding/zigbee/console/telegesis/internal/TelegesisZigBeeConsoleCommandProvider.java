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
package org.openhab.binding.zigbee.console.telegesis.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.openhab.binding.zigbee.telegesis.TelegesisBindingConstants.THING_TYPE_TELEGESIS;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.openhab.core.thing.ThingTypeUID;
import org.openhab.binding.zigbee.console.ZigBeeConsoleCommandProvider;
import org.osgi.service.component.annotations.Component;

import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand;
import com.zsmartsystems.zigbee.console.telegesis.TelegesisConsoleSecurityStateCommand;

/**
 * This class provides ZigBee console commands for Telegesis dongles.
 *
 * @author Henning Sudbrock - initial contribution
 */
@Component(immediate = true)
public class TelegesisZigBeeConsoleCommandProvider implements ZigBeeConsoleCommandProvider {

    public static final List<ZigBeeConsoleCommand> TELEGESIS_COMMANDS = unmodifiableList(
            asList(new TelegesisConsoleSecurityStateCommand()));

    private Map<String, ZigBeeConsoleCommand> telegesisCommands = TELEGESIS_COMMANDS.stream()
            .collect(toMap(ZigBeeConsoleCommand::getCommand, identity()));

    @Override
    public ZigBeeConsoleCommand getCommand(String commandName, ThingTypeUID thingTypeUID) {
        if (THING_TYPE_TELEGESIS.equals(thingTypeUID)) {
            return telegesisCommands.get(commandName);
        } else {
            return null;
        }
    }

    @Override
    public Collection<ZigBeeConsoleCommand> getAllCommands() {
        return TELEGESIS_COMMANDS;
    }
}
