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

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;
import static org.openhab.binding.zigbee.converter.warningdevice.SoundLevel.HIGH;

import java.util.Map;

import org.openhab.core.library.types.StringType;

/**
 * A type of squawk for a warning device.
 * <p>
 * Squawk types represented by this class can also be represented by ESH {@link StringType} commands; for this, a
 * rather simple format is used, by configuring the properties of the type using 'key=value' pairs that are
 * separated by whitespace.
 * <p>
 * Example for such a command: 'type=squawk squawkMode=ARMED useStrobe=true squawkLevel=HIGH'.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class SquawkType {

    private static final int DEFAULT_SQUAWK_MODE = SquawkMode.ARMED.getValue();
    private static final int DEFAULT_SQUAWK_LEVEL = HIGH.getValue();

    private final boolean useStrobe;
    private final int squawkMode;
    private final int squawkLevel;

    public SquawkType(boolean useStrobe, int squawkMode, int squawkLevel) {
        this.useStrobe = useStrobe;
        this.squawkMode = squawkMode;
        this.squawkLevel = squawkLevel;
    }

    /**
     * @return whether to use the optical strobe signal
     */
    public boolean isUseStrobe() {
        return useStrobe;
    }

    /**
     * @return the squawk mode to use
     */
    public int getSquawkMode() {
        return squawkMode;
    }

    /**
     * @return the squawk level to use
     */
    public int getSquawkLevel() {
        return squawkLevel;
    }

    /**
     * @return Generates the ESH command representing this warning
     */
    public String serializeToCommand() {
        return String.format("type=squawk useStrobe=%s squawkMode=%s squawkLevel=%s", useStrobe, squawkMode,
                squawkLevel);
    }

    /**
     * @param command An ESH command representing a warning
     * @return The {@link SquawkType} represented by the ESH command, or null if the command does not represent a
     *         {@link SquawkType}.
     */
    public static SquawkType parse(String command) {
        Map<String, String> parameters = stream(command.split("\\s+")).filter(s -> s.contains("="))
                .collect(toMap(s -> s.split("=")[0], s -> s.split("=")[1]));

        if ("squawk".equals(parameters.get("type"))) {
            return new SquawkType(Boolean.valueOf(parameters.getOrDefault("useStrobe", "true")),
                    getSquawkMode(parameters.get("squawkMode")), getSquawkLevel(parameters.get("squawkLevel")));
        } else {
            return null;
        }
    }

    private static int getSquawkMode(String squawkModeString) {
        if (squawkModeString == null) {
            return DEFAULT_SQUAWK_MODE;
        }

        try {
            return SquawkMode.valueOf(squawkModeString).getValue();
        } catch (IllegalArgumentException e) {
            // ignore - try to parse the warningModeString as number
        }

        try {
            return Integer.parseInt(squawkModeString);
        } catch (NumberFormatException e) {
            return DEFAULT_SQUAWK_MODE;
        }
    }

    private static int getSquawkLevel(String squawkLevelString) {
        if (squawkLevelString == null) {
            return DEFAULT_SQUAWK_LEVEL;
        }

        try {
            return SoundLevel.valueOf(squawkLevelString).getValue();
        } catch (IllegalArgumentException e) {
            // ignore - try to parse the squawkLevelString as number
        }

        try {
            return Integer.parseInt(squawkLevelString);
        } catch (NumberFormatException e) {
            return DEFAULT_SQUAWK_LEVEL;
        }
    }

}
