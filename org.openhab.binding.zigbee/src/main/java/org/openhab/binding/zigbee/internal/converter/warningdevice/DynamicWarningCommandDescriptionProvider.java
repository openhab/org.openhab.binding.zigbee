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
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import static java.util.stream.Collectors.toList;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_WARNING_DEVICE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.type.DynamicCommandDescriptionProvider;
import org.openhab.core.types.CommandDescription;
import org.openhab.core.types.CommandDescriptionBuilder;
import org.openhab.core.types.CommandOption;
import org.openhab.binding.zigbee.converter.warningdevice.WarningType;
import org.openhab.binding.zigbee.converter.warningdevice.WarningTypeCommandDescriptionProvider;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Provides the {@link CommandOption}s for ZigBee warning devices.
 * <p>
 * One command option is provided for every {@link WarningType}, where warning types
 * are obtained from (a) the {@link WarningTypeRegistry}, and (b) from the configuration
 * of the channel.
 *
 * @author Henning Sudbrock - initial contribution
 */
@Component(immediate = true)
public class DynamicWarningCommandDescriptionProvider implements DynamicCommandDescriptionProvider {

    private Set<WarningTypeCommandDescriptionProvider> providers = new CopyOnWriteArraySet<>();

    @Override
    public @Nullable CommandDescription getCommandDescription(@NonNull Channel channel,
            @Nullable CommandDescription originalCommandDescription, @Nullable Locale locale) {
        if (CHANNEL_WARNING_DEVICE.equals(channel.getChannelTypeUID())) {
            CommandDescriptionBuilder resultBuilder = CommandDescriptionBuilder.create();
            for (CommandOption commandOption : getCommandOptions(channel)) {
                resultBuilder.withCommandOption(commandOption);
            }
            return resultBuilder.build();
        } else {
            return null;
        }
    }

    @Reference(cardinality = MULTIPLE, policy = DYNAMIC)
    public void addProvider(WarningTypeCommandDescriptionProvider provider) {
        providers.add(provider);
    }

    public void removeProvider(WarningTypeCommandDescriptionProvider provider) {
        providers.remove(provider);
    }

    /**
     * @return all command options (provided and configured).
     */
    private List<CommandOption> getCommandOptions(Channel channel) {
        List<CommandOption> result = new ArrayList<>();
        result.addAll(getProvidedWarningTypes());
        result.addAll(getConfiguredWarningTypes(channel));
        return result;
    }

    private List<CommandOption> getProvidedWarningTypes() {
        return providers.stream().map(WarningTypeCommandDescriptionProvider::getWarningAndSquawkCommandOptions)
                .flatMap(List::stream).collect(toList());
    }

    private List<CommandOption> getConfiguredWarningTypes(Channel channel) {
        List<CommandOption> result = new ArrayList<>();

        Object commandOptionConfigObject = channel.getConfiguration().get("zigbee_iaswd_commandOptions");
        if (commandOptionConfigObject instanceof List) {
            @SuppressWarnings("rawtypes")
            List commandOptionConfigs = (List) commandOptionConfigObject;
            for (Object commandOptionConfig : commandOptionConfigs) {
                if (commandOptionConfig instanceof String) {
                    String[] parts = ((String) commandOptionConfig).split("=>");
                    if (parts.length == 2) {
                        result.add(new CommandOption(parts[1], parts[0]));
                    }
                }
            }
        }

        return result;
    }

}
