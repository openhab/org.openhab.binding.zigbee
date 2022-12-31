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

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_WARNING_DEVICE;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.zigbee.converter.warningdevice.WarningType;
import org.openhab.binding.zigbee.converter.warningdevice.WarningTypeCommandDescriptionProvider;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.CommandDescription;
import org.openhab.core.types.CommandOption;

/**
 * Unit tests for the {@link DynamicWarningCommandDescriptionProvider}.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class DynamicWarningCommandDescriptionProviderTest {

    private DynamicWarningCommandDescriptionProvider provider;

    @BeforeEach
    public void setup() {
        provider = new DynamicWarningCommandDescriptionProvider();
    }

    @Test
    public void testGetCommandDescriptionsWithProviderCommandDescription() {
        WarningType providedWarningType = WarningType.parse("type=warning");
        provider.addProvider(makeWarningTypeCommandDescriptionProvider("someLabel", providedWarningType));

        Channel channel = ChannelBuilder.create(new ChannelUID("binding:thing-type:thing:channel"), "String")
                .withType(CHANNEL_WARNING_DEVICE).build();

        CommandDescription commandDescription = provider.getCommandDescription(channel, null, null);
        assertNotNull(commandDescription);

        List<CommandOption> commandOptions = commandDescription.getCommandOptions();
        assertEquals(1, commandOptions.size());
        assertEquals("someLabel", commandOptions.get(0).getLabel());
        assertEquals(providedWarningType.serializeToCommand(), commandOptions.get(0).getCommand());
    }

    @Test
    public void testGetCommandDescriptionsWithConfiguredCommandDescription() {
        WarningType configuredWarningType = WarningType.parse("type=warning");
        Configuration configuration = new Configuration();
        configuration.put("zigbee_iaswd_commandOptions",
                asList("someLabel=>" + configuredWarningType.serializeToCommand()));
        Channel channel = ChannelBuilder.create(new ChannelUID("binding:thing-type:thing:channel"), "String")
                .withType(CHANNEL_WARNING_DEVICE).withConfiguration(configuration).build();

        CommandDescription commandDescription = provider.getCommandDescription(channel, null, null);
        assertNotNull(commandDescription);

        List<CommandOption> commandOptions = commandDescription.getCommandOptions();
        assertEquals(1, commandOptions.size());
        assertEquals("someLabel", commandOptions.get(0).getLabel());
        assertEquals(configuredWarningType.serializeToCommand(), commandOptions.get(0).getCommand());
    }

    @Test
    public void testGetCommandDescriptionsWithOtherChannelType() {
        Channel channel = ChannelBuilder.create(new ChannelUID("binding:thing-type:thing:channel"), "String").build();

        CommandDescription commandDescription = provider.getCommandDescription(channel, null, null);
        assertNull(commandDescription);
    }

    private WarningTypeCommandDescriptionProvider makeWarningTypeCommandDescriptionProvider(final String label,
            final WarningType warningType) {
        return new WarningTypeCommandDescriptionProvider() {

            @Override
            public List<CommandOption> getWarningAndSquawkCommandOptions() {
                return Collections.singletonList(new CommandOption(warningType.serializeToCommand(), label));
            }
        };
    }

}
