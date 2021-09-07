/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.console.internal;

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

import com.zsmartsystems.zigbee.console.ZigBeeConsoleAttributeReadCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleAttributeSupportedCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleAttributeWriteCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleBindCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleBindingTableCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleChannelCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommandsSupportedCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleDescribeEndpointCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleDescribeNodeCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleDeviceFingerprintCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleDeviceInformationCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleFirmwareCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleGroupCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleInstallKeyCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleLinkKeyCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleNeighborsListCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleNetworkBackupCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleNetworkJoinCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleNetworkLeaveCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleNodeListCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleOtaUpgradeCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleReportingConfigCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleReportingSubscribeCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleReportingUnsubscribeCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleRoutingTableCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleSceneCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleUnbindCommand;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleWindowCoveringCommand;

/**
 * This class provides general ZigBee console commands that are not tied to a specific ZigBee dongle.
 *
 * @author Henning Sudbrock - initial contribution
 * @author Chris Jackson - added commands
 */
@Component
public class GeneralZigBeeConsoleCommandProvider implements ZigBeeConsoleCommandProvider {

    @SuppressWarnings("null")
    public static final List<ZigBeeConsoleCommand> GENERAL_COMMANDS = unmodifiableList(asList(
            new ZigBeeConsoleAttributeReadCommand(), new ZigBeeConsoleAttributeSupportedCommand(),
            new ZigBeeConsoleAttributeWriteCommand(), new ZigBeeConsoleBindCommand(),
            new ZigBeeConsoleBindingTableCommand(), new ZigBeeConsoleChannelCommand(),
            new ZigBeeConsoleCommandsSupportedCommand(), new ZigBeeConsoleDescribeEndpointCommand(),
            new ZigBeeConsoleDescribeNodeCommand(), new ZigBeeConsoleDeviceFingerprintCommand(),
            new ZigBeeConsoleDeviceInformationCommand(), new ZigBeeConsoleFirmwareCommand(),
            new ZigBeeConsoleGroupCommand(), new ZigBeeConsoleInstallKeyCommand(), new ZigBeeConsoleLinkKeyCommand(),
            new ZigBeeConsoleNeighborsListCommand(), new ZigBeeConsoleNetworkBackupCommand(),
            new ZigBeeConsoleNetworkJoinCommand(), new ZigBeeConsoleNetworkLeaveCommand(),
            new ZigBeeConsoleNodeListCommand(), new ZigBeeConsoleOtaUpgradeCommand(), new ZigBeeConsoleSceneCommand(),
            new ZigBeeConsoleReportingConfigCommand(), new ZigBeeConsoleReportingSubscribeCommand(),
            new ZigBeeConsoleReportingUnsubscribeCommand(), new ZigBeeConsoleRoutingTableCommand(),
            new ZigBeeConsoleUnbindCommand(), new ZigBeeConsoleWindowCoveringCommand()));

    private Map<String, ZigBeeConsoleCommand> generalCommands = GENERAL_COMMANDS.stream()
            .collect(toMap(ZigBeeConsoleCommand::getCommand, identity()));

    @Override
    public ZigBeeConsoleCommand getCommand(String commandName, ThingTypeUID thingTypeUID) {
        return generalCommands.get(commandName);
    }

    @Override
    public Collection<ZigBeeConsoleCommand> getAllCommands() {
        return GENERAL_COMMANDS;
    }
}
