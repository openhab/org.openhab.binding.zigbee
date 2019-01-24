/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.CHANNEL_WARNING_DEVICE;

import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.CommandDescription;
import org.eclipse.smarthome.core.types.CommandOption;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the {@link DynamicWarningCommandDescriptionProvider}.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class DynamicWarningCommandDescriptionProviderTest {

    private DynamicWarningCommandDescriptionProvider provider;

    @Before
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

        List<@NonNull CommandOption> commandOptions = commandDescription.getCommandOptions();
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

        List<@NonNull CommandOption> commandOptions = commandDescription.getCommandOptions();
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

    private WarningTypeCommandDescriptionProvider makeWarningTypeCommandDescriptionProvider(String label,
            WarningType warningType) {
        return new WarningTypeCommandDescriptionProvider() {

            @Override
            public List<CommandOption> getWarningAndSquawCommandOptions() {
                return Collections.singletonList(new CommandOption(warningType.serializeToCommand(), label));
            }
        };
    }

}
