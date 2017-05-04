/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.smarthome.config.core.ConfigDescription;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter.Type;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterBuilder;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterGroup;
import org.eclipse.smarthome.config.core.ConfigDescriptionProvider;
import org.eclipse.smarthome.config.core.ConfigDescriptionRegistry;
import org.eclipse.smarthome.config.core.ConfigOptionProvider;
import org.eclipse.smarthome.config.core.ParameterOption;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingRegistry;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.thing.type.ThingTypeRegistry;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zdo.descriptors.NodeDescriptor.LogicalType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class ZigBeeConfigProvider implements ConfigDescriptionProvider, ConfigOptionProvider {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeConfigProvider.class);

    private static ThingRegistry thingRegistry;
    private static ThingTypeRegistry thingTypeRegistry;
    private static ConfigDescriptionRegistry configDescriptionRegistry;

    private static Set<ThingTypeUID> zigbeeThingTypeUIDList = new HashSet<ThingTypeUID>();

    protected void setThingRegistry(ThingRegistry thingRegistry) {
        ZigBeeConfigProvider.thingRegistry = thingRegistry;
    }

    protected void unsetThingRegistry(ThingRegistry thingRegistry) {
        ZigBeeConfigProvider.thingRegistry = null;
    }

    protected void setThingTypeRegistry(ThingTypeRegistry thingTypeRegistry) {
        ZigBeeConfigProvider.thingTypeRegistry = thingTypeRegistry;
    }

    protected void unsetThingTypeRegistry(ThingTypeRegistry thingTypeRegistry) {
        ZigBeeConfigProvider.thingTypeRegistry = null;
    }

    protected void setConfigDescriptionRegistry(ConfigDescriptionRegistry configDescriptionRegistry) {
        ZigBeeConfigProvider.configDescriptionRegistry = configDescriptionRegistry;
    }

    protected void unsetConfigDescriptionRegistry(ConfigDescriptionRegistry configDescriptionRegistry) {
        ZigBeeConfigProvider.configDescriptionRegistry = null;
    }

    public static Thing getThing(ThingUID thingUID) {
        // Check that we know about the registry
        if (thingRegistry == null) {
            return null;
        }

        return thingRegistry.get(thingUID);
    }

    public static ThingType getThingType(ThingTypeUID thingTypeUID) {
        // Check that we know about the registry
        if (thingTypeRegistry == null) {
            return null;
        }

        return thingTypeRegistry.getThingType(thingTypeUID);
    }

    @Override
    public Collection<ConfigDescription> getConfigDescriptions(Locale locale) {
        logger.debug("getConfigDescriptions called");
        return Collections.emptySet();
    }

    @Override
    public ConfigDescription getConfigDescription(URI uri, Locale locale) {
        if (uri == null) {
            return null;
        }

        if ("thing".equals(uri.getScheme()) == false) {
            return null;
        }

        ThingUID thingUID = new ThingUID(uri.getSchemeSpecificPart());

        // Is this a zigbee thing?
        if (!thingUID.getBindingId().equals(ZigBeeBindingConstants.BINDING_ID)) {
            return null;
        }

        // String nodeIeeeAddressString = thingUID.getId();
        IeeeAddress nodeIeeeAddress = null;// new IeeeAddress(nodeIeeeAddressString);

        Thing thing = getThing(thingUID);
        if (thing == null) {
            return null;
        }

        ZigBeeCoordinatorHandler coordinator = null;
        if (thing.getHandler() instanceof ZigBeeCoordinatorHandler) {
            coordinator = (ZigBeeCoordinatorHandler) thing.getHandler();
            nodeIeeeAddress = coordinator.getIeeeAddress();
        } else if (thing.getHandler() instanceof ZigBeeThingHandler) {
            ZigBeeThingHandler zigbeeThing = (ZigBeeThingHandler) thing.getHandler();
            nodeIeeeAddress = zigbeeThing.getIeeeAddress();
            coordinator = zigbeeThing.getCoordinatorHandler();
        } else {
            return null;
        }

        // Find the zigbee node
        ZigBeeNode node = coordinator.getNode(nodeIeeeAddress);
        if (node == null) {
            return null;
        }

        // Ok, we're good - create the lists of groups and parameters
        List<ConfigDescriptionParameterGroup> groups = new ArrayList<ConfigDescriptionParameterGroup>();
        List<ConfigDescriptionParameter> parameters = new ArrayList<ConfigDescriptionParameter>();

        groups.add(new ConfigDescriptionParameterGroup("actions", "", false, "Actions", null));
        groups.add(new ConfigDescriptionParameterGroup("thingcfg", "home", false, "Device Configuration", null));

        // parameters.add(
        // ConfigDescriptionParameterBuilder.create(ZigBeeBindingConstants.THING_PARAMETER_MACADDRESS, Type.TEXT)
        // .withLabel("MAC Address").withAdvanced(true).withReadOnly(true).withRequired(true)
        // .withDescription(
        // "Sets the node IEEE address<BR/>The node address is unique for each device and can not be changed.")
        // .withDefault("").withGroupName("thingcfg").build());

        // For coordinators and routers, we need to have the option to join and leave
        if (node.getLogicalType() == LogicalType.COORDINATOR || node.getLogicalType() == LogicalType.ROUTER) {
            parameters.add(ConfigDescriptionParameterBuilder
                    .create(ZigBeeBindingConstants.CONFIGURATION_JOINENABLE, Type.BOOLEAN).withLabel("Enable join")
                    .withAdvanced(true).withDescription("Enables join mode for this device for 1 minute.")
                    .withDefault("").withGroupName("actions").build());
        }

        if (node.getLogicalType() != LogicalType.COORDINATOR) {
            parameters.add(ConfigDescriptionParameterBuilder
                    .create(ZigBeeBindingConstants.CONFIGURATION_LEAVE, Type.BOOLEAN).withLabel("Leave network")
                    .withAdvanced(true).withDescription("Requests that the node leave the network").withDefault("")
                    .withGroupName("actions").build());
        }

        return new ConfigDescription(uri, parameters, groups);
    }

    @Override
    public Collection<ParameterOption> getParameterOptions(URI uri, String param, Locale locale) {
        // TODO Auto-generated method stub
        return null;
    }
}