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
package org.openhab.binding.zigbee.console;

import java.util.Collection;

import org.openhab.core.thing.ThingTypeUID;

import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand;

/**
 * Interface to be implemented by services providing {@link ZigBeeConsoleCommand} instances.
 *
 * @author Henning Sudbrock - initial contribution
 */
public interface ZigBeeConsoleCommandProvider {

    /**
     * Returns all provided commands.
     */
    Collection<ZigBeeConsoleCommand> getAllCommands();

    /**
     * Returns a console command for the given name and applicable for the given bridge type, or null if no such command
     * is provided.
     */
    ZigBeeConsoleCommand getCommand(String commandName, ThingTypeUID thingTypeUID);

}
