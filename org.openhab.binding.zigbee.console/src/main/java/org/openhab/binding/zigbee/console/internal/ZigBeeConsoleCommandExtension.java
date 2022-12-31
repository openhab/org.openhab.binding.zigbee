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
package org.openhab.binding.zigbee.console.internal;

import static java.util.stream.Collectors.toList;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.BINDING_ID;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.openhab.binding.zigbee.console.ZigBeeConsoleCommandProvider;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.core.io.console.Console;
import org.openhab.core.io.console.extensions.AbstractConsoleCommandExtension;
import org.openhab.core.io.console.extensions.ConsoleCommandExtension;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand;

/**
 * Console command extension that delegates commands to the ZigBee console of the underlying ZigBee core framework.
 *
 * @author Henning Sudbrock - initial contribution
 */
@Component(immediate = true, service = ConsoleCommandExtension.class)
public class ZigBeeConsoleCommandExtension extends AbstractConsoleCommandExtension {

    private ThingRegistry thingRegistry;

    private Set<ZigBeeConsoleCommandProvider> commandProviders = new CopyOnWriteArraySet<>();

    private ThingUID bridgeUID;

    public ZigBeeConsoleCommandExtension() {
        super("zigbee", "ZigBee console commands");
    }

    @Reference
    public void setThingRegistry(ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
    }

    public void unsetThingRegistry(ThingRegistry thingRegistry) {
        this.thingRegistry = null;
    }

    @Reference(cardinality = MULTIPLE, policy = DYNAMIC)
    public void addCommandProvider(ZigBeeConsoleCommandProvider commandProvider) {
        commandProviders.add(commandProvider);
    }

    public void removeCommandProvider(ZigBeeConsoleCommandProvider commandProvider) {
        commandProviders.remove(commandProvider);
    }

    @Override
    public void execute(String[] args, Console console) {
        if (args.length == 0) {
            printUsage(console);
            return;
        } else {
            String commandName = args[0];
            try {
                String result = handleCommand(commandName, args);
                console.println(result);
            } catch (CommandExecutionException e) {
                console.println("Error: " + e.getMessage());
            }
        }
    }

    @Override
    public List<String> getUsages() {
        Stream<ZigBeeConsoleCommand> zigBeeConsoleCommands = commandProviders.stream().map(p -> p.getAllCommands())
                .flatMap(l -> l.stream());

        List<String> result = zigBeeConsoleCommands
                .map(consoleCommand -> String.format("%s %s - %s", consoleCommand.getCommand(),
                        consoleCommand.getSyntax(), consoleCommand.getDescription()))
                .map(this::buildCommandUsage).collect(toList());

        result.add(buildCommandUsage(
                "setBridgeUid [bridgeUid] - sets the UID of the bridge to be used in subsequent commands; only required if there is more than 1 bridge"));

        return result;
    }

    private String handleCommand(String commandName, String[] args) throws CommandExecutionException {
        if ("setBridgeUid".equals(commandName)) {
            return handleSetBridgeUidCommand(args);
        } else {
            return handleZigbeeCommand(commandName, args);
        }
    }

    private String handleSetBridgeUidCommand(String[] args) throws CommandExecutionException {
        if (args.length == 1) {
            bridgeUID = null;
            return "Cleared bridge UID";
        } else if (args.length > 2) {
            throw new CommandExecutionException("Please provide exactly one parameter containing the bridge UID");
        } else {
            try {
                bridgeUID = new ThingUID(args[1]);
                return "Set bridge UID to " + bridgeUID;
            } catch (IllegalArgumentException e) {
                throw new CommandExecutionException("Please provide a valid bridge UID");
            }
        }
    }

    private String handleZigbeeCommand(String commandName, String[] args) throws CommandExecutionException {
        Bridge zigBeeBridge = getBridge();
        ZigBeeConsoleCommand zigbeeCommand = getZigBeeCommand(commandName, zigBeeBridge);
        ZigBeeCoordinatorHandler bridgeHandler = (ZigBeeCoordinatorHandler) zigBeeBridge.getHandler();
        if (bridgeHandler == null) {
            throw new CommandExecutionException("Could not find bridge handler for bridge");
        }

        ZigBeeNetworkManager networkManager = bridgeHandler.getNetworkManager();

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(byteArrayOutputStream)) {
            zigbeeCommand.process(networkManager, args, printStream);
            return byteArrayOutputStream.toString();
        } catch (IOException | IllegalArgumentException | IllegalStateException | ExecutionException
                | InterruptedException exception) {
            throw new CommandExecutionException("Exception during command execution ("
                    + exception.getClass().getSimpleName() + "): " + exception.getMessage());
        }
    }

    private ZigBeeConsoleCommand getZigBeeCommand(String commandName, Bridge bridge) throws CommandExecutionException {
        return commandProviders.stream().map(p -> p.getCommand(commandName, bridge.getThingTypeUID()))
                .filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> new CommandExecutionException("Could not find command: " + commandName));
    }

    private Bridge getBridge() throws CommandExecutionException {
        if (bridgeUID != null) {
            Thing thing = thingRegistry.get(bridgeUID);
            if (thing != null && BINDING_ID.equals(thing.getThingTypeUID().getBindingId()) && thing instanceof Bridge) {
                return (Bridge) thing;
            } else {
                throw new CommandExecutionException("Could not find bridge with ID " + bridgeUID);
            }
        } else {
            List<Thing> bridges = thingRegistry.stream().filter(
                    thing -> BINDING_ID.equals(thing.getThingTypeUID().getBindingId()) && thing instanceof Bridge)
                    .collect(toList());
            if (bridges.isEmpty()) {
                throw new CommandExecutionException("No ZigBee bridge found");
            } else if (bridges.size() > 1) {
                throw new CommandExecutionException(
                        "Multiple ZigBee bridges found; please select one using the setBridgeUid command");
            } else {
                return (Bridge) bridges.get(0);
            }
        }
    }

    private class CommandExecutionException extends Exception {
        private static final long serialVersionUID = 1L;

        public CommandExecutionException(String message) {
            super(message);
        }
    }
}
