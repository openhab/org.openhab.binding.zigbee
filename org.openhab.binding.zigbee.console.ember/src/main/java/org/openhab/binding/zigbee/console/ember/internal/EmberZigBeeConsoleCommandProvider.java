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
package org.openhab.binding.zigbee.console.ember.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.openhab.binding.zigbee.console.ZigBeeConsoleCommandProvider;
import org.openhab.core.thing.ThingTypeUID;
import org.osgi.service.component.annotations.Component;

import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleMmoHashCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpAddressTableCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpChildrenCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpConfigurationCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpCountersCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpHandlerCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpMulticastCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpPolicyCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpRoutingCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpScanCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpStateCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpTokenCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpValueCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleNcpVersionCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleSecurityStateCommand;
import com.zsmartsystems.zigbee.console.ember.EmberConsoleTransientKeyCommand;

/**
 * This class provides ZigBee console commands for Ember dongles.
 *
 * @author Henning Sudbrock - initial contribution
 * @author Chris Jackson - added commands
 */
@Component(immediate = true)
public class EmberZigBeeConsoleCommandProvider implements ZigBeeConsoleCommandProvider {
    // These constants are defined locally to avoid unnecessary dependencies between coordinators
    private static final String BINDING_ID = "zigbee";
    private static final ThingTypeUID THING_TYPE_EMBER = new ThingTypeUID(BINDING_ID, "coordinator_ember");
    private static final ThingTypeUID THING_TYPE_SLZB06 = new ThingTypeUID(BINDING_ID, "coordinator_slzb06");

    @SuppressWarnings("null")
    public static final List<ZigBeeConsoleCommand> EMBER_COMMANDS = unmodifiableList(asList(
            new EmberConsoleMmoHashCommand(), new EmberConsoleNcpAddressTableCommand(),
            new EmberConsoleNcpChildrenCommand(), new EmberConsoleNcpConfigurationCommand(),
            new EmberConsoleNcpCountersCommand(), new EmberConsoleNcpHandlerCommand(),
            new EmberConsoleNcpMulticastCommand(), new EmberConsoleNcpPolicyCommand(),
            new EmberConsoleNcpRoutingCommand(), new EmberConsoleNcpScanCommand(), new EmberConsoleNcpStateCommand(),
            new EmberConsoleSecurityStateCommand(), new EmberConsoleNcpStateCommand(),
            new EmberConsoleNcpTokenCommand(), new EmberConsoleNcpValueCommand(), new EmberConsoleNcpVersionCommand(),
            new EmberConsoleTransientKeyCommand()));

    private Map<String, ZigBeeConsoleCommand> emberCommands = EMBER_COMMANDS.stream()
            .collect(toMap(ZigBeeConsoleCommand::getCommand, identity()));

    @Override
    public ZigBeeConsoleCommand getCommand(String commandName, ThingTypeUID thingTypeUID) {
        if (THING_TYPE_EMBER.equals(thingTypeUID) || THING_TYPE_SLZB06.equals(thingTypeUID)) {
            return emberCommands.get(commandName);
        } else {
            return null;
        }
    }

    @Override
    public Collection<ZigBeeConsoleCommand> getAllCommands() {
        return EMBER_COMMANDS;
    }
}
