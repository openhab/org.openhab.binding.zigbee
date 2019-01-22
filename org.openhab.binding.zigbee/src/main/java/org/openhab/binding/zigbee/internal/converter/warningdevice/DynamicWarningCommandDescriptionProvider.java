/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import static java.util.stream.Collectors.toMap;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_WARNING_DEVICE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.type.DynamicCommandDescriptionProvider;
import org.eclipse.smarthome.core.types.CommandDescription;
import org.eclipse.smarthome.core.types.CommandDescriptionBuilder;
import org.eclipse.smarthome.core.types.CommandOption;
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
            for (Entry<String, WarningType> warningType : getWarningTypes(channel).entrySet()) {
                resultBuilder.withCommandOption(
                        new CommandOption(warningType.getValue().serializeToCommand(), warningType.getKey()));
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
     * @return all provider {@link WarningType}s.
     */
    private Map<String, WarningType> getWarningTypes(Channel channel) {
        Map<String, WarningType> result = new HashMap<>();
        result.putAll(getProvidedWarningTypes());
        result.putAll(getConfiguredWarningTypes(channel));
        return result;
    }

    private Map<String, WarningType> getProvidedWarningTypes() {
        return providers.stream().map(WarningTypeCommandDescriptionProvider::getWarningTypes)
                .flatMap(map -> map.entrySet().stream()).collect(toMap(Entry::getKey, Entry::getValue));
    }

    private Map<String, WarningType> getConfiguredWarningTypes(Channel channel) {
        Map<String, WarningType> result = new HashMap<>();

        Object warningTypeConfigObject = channel.getConfiguration().get("zigbee_iaswd_warningTypes");
        if (warningTypeConfigObject instanceof List) {
            @SuppressWarnings("rawtypes")
            List warningTypeConfigs = (List) warningTypeConfigObject;
            for (Object warningTypeConfig : warningTypeConfigs) {
                if (warningTypeConfig instanceof String) {
                    String[] parts = ((String) warningTypeConfig).split("=>");
                    if (parts.length == 2) {
                        result.put(parts[0], WarningType.parse(parts[1]));
                    }
                }
            }
        }

        return result;
    }

}
