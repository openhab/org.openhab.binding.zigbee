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
package org.openhab.binding.zigbee.converter.warningdevice;

import java.util.List;

import org.openhab.core.types.CommandOption;

/**
 * Interface for providers of additional warning device configurations that are provided as command options for warning
 * device channels.
 *
 * @author Henning Sudbrock - initial contribution
 */
public interface WarningTypeCommandDescriptionProvider {

    /**
     * @return A map mapping labels for warning/squawk command descriptions (to be used by UIs) to the serializes
     *         warning/squawk commands.
     */
    List<CommandOption> getWarningAndSquawkCommandOptions();

}
