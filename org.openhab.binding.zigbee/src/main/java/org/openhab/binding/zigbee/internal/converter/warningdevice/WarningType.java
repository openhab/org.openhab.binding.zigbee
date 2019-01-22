/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.openhab.binding.zigbee.internal.converter.warningdevice.SirenLevel.HIGH;
import static org.openhab.binding.zigbee.internal.converter.warningdevice.WarningMode.BURGLAR;

import java.time.Duration;
import java.util.Map;

import org.eclipse.smarthome.core.library.types.StringType;

/**
 * A type of warning for a warning device.
 * <p>
 * Warning types represented by this class can also be represented by ESH {@link StringType} commands; for this, a
 * rather simple format is used, by configuring the properties of the type using 'key=value' pairs that are
 * separated by whitespace.
 * <p>
 * Example for such a command: 'useStrobe=true warningMode=BURGLAR sirenLevel=HIGH duration=PT15M'.
 * <p>
 * Note that the duration is specified using the ISO-8601 duration format (see {@link Duration#parse(CharSequence)} for
 * more details).
 *
 * @author Henning Sudbrock - initial contribution
 */
public class WarningType {

    private final boolean useStrobe;
    private final WarningMode warningMode;
    private final SirenLevel sirenLevel;
    private final Duration duration;

    public WarningType(boolean useStrobe, WarningMode warningMode, SirenLevel sirenLevel, Duration duration) {
        requireNonNull(warningMode);
        requireNonNull(sirenLevel);
        requireNonNull(duration);

        this.useStrobe = useStrobe;
        this.warningMode = warningMode;
        this.sirenLevel = sirenLevel;
        this.duration = duration;
    }

    /**
     * @return whether to use the optical strobe signal
     */
    public boolean isUseStrobe() {
        return useStrobe;
    }

    /**
     * @return the {@link WarningMode} to use
     */
    public WarningMode getWarningMode() {
        return warningMode;
    }

    /**
     * @return the {@link SirenLevel} to use
     */
    public SirenLevel getSirenLevel() {
        return sirenLevel;
    }

    /**
     * @return the duration of the warning
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * @return Generates the ESH command representing this warning
     */
    public String serializeToCommand() {
        return String.format("useStrobe=%s warningMode=%s sirenLevel=%s duration=%s", useStrobe, warningMode,
                sirenLevel, duration);
    }

    /**
     * @param command An ESH command representing a warning
     * @return The {@link WarningType} represented by the ESH command
     */
    public static WarningType parse(String command) {
        Map<String, String> parameters = stream(command.split("\\s+")).filter(s -> s.contains("="))
                .collect(toMap(s -> s.split("=")[0], s -> s.split("=")[1]));

        return new WarningType(Boolean.valueOf(parameters.getOrDefault("useStrobe", "true")),
                WarningMode.valueOf(parameters.getOrDefault("warningMode", BURGLAR.toString())),
                SirenLevel.valueOf(parameters.getOrDefault("sirenLevel", HIGH.toString())),
                Duration.parse(parameters.getOrDefault("duration", Duration.ofSeconds(15).toString())));
    }

}
