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
package org.openhab.binding.zigbee.internal.converter;

import static java.lang.Integer.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.openhab.core.thing.Channel;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.ThingUID;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclScenesCluster;

/**
 * Generic converter for buttons (e.g., from remote controls).
 * <p>
 * This converter needs to be configured with the ZigBee commands that are triggered by the button presses. This is done
 * by channel properties that specify the endpoint, the cluster, the command ID, and (optionally) a command parameter.
 * <p>
 * As the configuration is done via channel properties, this converter is usable via static thing types only.
 *
 * @author Henning Sudbrock - initial contribution
 * @author Thomas Weißschuh - support for attribute-based buttons
 */
public class ZigBeeConverterGenericButton extends ZigBeeBaseChannelConverter
        implements ZclCommandListener, ZclAttributeListener {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String CLUSTER = "cluster_id";
    private static final String COMMAND = "command_id";
    private static final String PARAM_NAME = "parameter_name";
    private static final String PARAM_VALUE = "parameter_value";
    private static final String ATTRIBUTE_ID = "attribute_id";
    private static final String ATTRIBUTE_VALUE = "attribute_value";

    private Map<ButtonPressType, EventSpec> handledEvents = new EnumMap<>(ButtonPressType.class);
    private Set<ZclCluster> clientClusters = new HashSet<>();
    private Set<ZclCluster> serverClusters = new HashSet<>();

    @Override
    public Set<Integer> getImplementedClientClusters() {
        return Collections.emptySet();
    }

    @Override
    public Set<Integer> getImplementedServerClusters() {
        return Collections.singleton(ZclScenesCluster.CLUSTER_ID);
    }

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing) {
        super.initializeConverter(thing);
        for (ButtonPressType buttonPressType : ButtonPressType.values()) {
            EventSpec eventSpec = parseEventSpec(channel.getProperties(), buttonPressType);
            if (eventSpec != null) {
                handledEvents.put(buttonPressType, eventSpec);
            }
        }

        if (handledEvents.isEmpty()) {
            logger.error("{}: No command is specified for any of the possible button press types in channel {}",
                    endpoint.getIeeeAddress(), channel.getUID());
            return false;
        }

        boolean allBindsSucceeded = true;

        for (EventSpec eventSpec : handledEvents.values()) {
            allBindsSucceeded &= eventSpec.bindCluster();
        }

        return allBindsSucceeded;
    }

    @Override
    public void disposeConverter() {
        for (ZclCluster clientCluster : clientClusters) {
            logger.debug("{}: Closing client cluster {}", endpoint.getIeeeAddress(), clientCluster.getClusterId());
            clientCluster.removeCommandListener(this);
        }

        for (ZclCluster serverCluster : serverClusters) {
            logger.debug("{}: Closing server cluster {}", endpoint.getIeeeAddress(), serverCluster.getClusterId());
            serverCluster.removeAttributeListener(this);
        }
    }

    @Override
    public void handleRefresh() {
        // nothing to do, as we only listen to commands
    }

    @Override
    public Channel getChannel(ThingUID thingUID, ZigBeeEndpoint endpoint) {
        // This converter is used only for channels specified in static thing types, and cannot be used to construct
        // channels based on an endpoint alone.
        return null;
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        ButtonPressType buttonPressType = getButtonPressType(command);
        if (buttonPressType != null) {
            logger.debug("{}: Matching ZigBee command for press type {} received: {}", endpoint.getIeeeAddress(),
                    buttonPressType, command);
            thing.triggerChannel(channel.getUID(), getEvent(buttonPressType));

            return true;
        }

        return false;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object value) {
        ButtonPressType buttonPressType = getButtonPressType(attribute, value);
        if (buttonPressType != null) {
            logger.debug("{}: Matching ZigBee attribute for press type {} received: {}", endpoint.getIeeeAddress(),
                    buttonPressType, attribute);
            thing.triggerChannel(channel.getUID(), getEvent(buttonPressType));
        }
    }

    private String getEvent(ButtonPressType pressType) {
        switch (pressType) {
            case DOUBLE_PRESS:
                return CommonTriggerEvents.DOUBLE_PRESSED;
            case LONG_PRESS:
                return CommonTriggerEvents.LONG_PRESSED;
            case SHORT_PRESS:
                return CommonTriggerEvents.SHORT_PRESSED;
            default:
                logger.warn("Stumbled upon invalid presstype: {}", pressType);
                return null;
        }
    }

    private ButtonPressType getButtonPressType(ZclAttribute attribute, Object value) {
        return getButtonPressType(cs -> cs.matches(attribute, value));
    }

    private ButtonPressType getButtonPressType(ZclCommand command) {
        return getButtonPressType(cs -> cs.matches(command));
    }

    private ButtonPressType getButtonPressType(Predicate<EventSpec> predicate) {
        for (Entry<ButtonPressType, EventSpec> entry : handledEvents.entrySet()) {
            if (predicate.test(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private EventSpec parseEventSpec(Map<String, String> properties, ButtonPressType pressType) {
        String clusterProperty = properties.get(getParameterName(CLUSTER, pressType));

        if (clusterProperty == null) {
            return null;
        }

        int clusterId;

        try {
            clusterId = parseId(clusterProperty);
        } catch (NumberFormatException e) {
            logger.warn("{}: Could not parse cluster property {}", endpoint.getIeeeAddress(), clusterProperty);
            return null;
        }

        boolean hasCommand = properties.containsKey(getParameterName(COMMAND, pressType));
        boolean hasAttribute = properties.containsKey(getParameterName(ATTRIBUTE_ID, pressType));

        if (hasCommand && hasAttribute) {
            logger.warn("{}: Only one of command or attribute can be used", endpoint.getIeeeAddress());
            return null;
        }

        if (hasCommand) {
            return parseCommandSpec(clusterId, properties, pressType);
        } else {
            return parseAttributeReportSpec(clusterId, properties, pressType);
        }
    }

    private AttributeReportSpec parseAttributeReportSpec(int clusterId, Map<String, String> properties,
            ButtonPressType pressType) {
        String attributeIdProperty = properties.get(getParameterName(ATTRIBUTE_ID, pressType));
        String attributeValue = properties.get(getParameterName(ATTRIBUTE_VALUE, pressType));

        if (attributeIdProperty == null) {
            logger.warn("{}: Missing attribute id", endpoint.getIeeeAddress());
            return null;
        }

        Integer attributeId;

        try {
            attributeId = parseId(attributeIdProperty);
        } catch (NumberFormatException e) {
            logger.warn("{}: Could not parse attribute property {}", endpoint.getIeeeAddress(), attributeIdProperty);
            return null;
        }

        if (attributeValue == null) {
            logger.warn("{}: No attribute value for attribute {} specified", endpoint.getIeeeAddress(), attributeId);
            return null;
        }

        return new AttributeReportSpec(clusterId, attributeId, attributeValue);
    }

    private CommandSpec parseCommandSpec(int clusterId, Map<String, String> properties, ButtonPressType pressType) {
        String commandProperty = properties.get(getParameterName(COMMAND, pressType));
        String commandParameterName = properties.get(getParameterName(PARAM_NAME, pressType));
        String commandParameterValue = properties.get(getParameterName(PARAM_VALUE, pressType));

        if (commandProperty == null) {
            logger.warn("{}: Missing command", endpoint.getIeeeAddress());
            return null;
        }

        Integer commandId;

        try {
            commandId = parseId(commandProperty);
        } catch (NumberFormatException e) {
            logger.warn("{}: Could not parse command property {}", endpoint.getIeeeAddress(), commandProperty);
            return null;
        }

        if ((commandParameterName != null && commandParameterValue == null)
                || (commandParameterName == null && commandParameterValue != null)) {
            logger.warn("{}: When specifiying a command parameter, both name and value must be specified",
                    endpoint.getIeeeAddress());
            return null;
        }

        return new CommandSpec(clusterId, commandId, commandParameterName, commandParameterValue);
    }

    private static String getParameterName(String parameterType, ButtonPressType buttonPressType) {
        return String.format("zigbee_%s_%s", buttonPressType, parameterType);
    }

    private static int parseId(String id) throws NumberFormatException {
        if (id.startsWith("0x")) {
            return parseInt(id.substring(2), 16);
        } else {
            return parseInt(id);
        }
    }

    private enum ButtonPressType {
        SHORT_PRESS("shortpress"),
        DOUBLE_PRESS("doublepress"),
        LONG_PRESS("longpress");

        private String parameterValue;

        private ButtonPressType(String parameterValue) {
            this.parameterValue = parameterValue;
        }

        @Override
        public String toString() {
            return parameterValue;
        }
    }

    private abstract class EventSpec {
        private final int clusterId;

        EventSpec(int clusterId) {
            this.clusterId = clusterId;
        }

        int getClusterId() {
            return clusterId;
        }

        abstract boolean matches(ZclCommand command);

        abstract boolean matches(ZclAttribute attribute, Object value);

        abstract boolean bindCluster();

        boolean bindCluster(String clusterType, Collection<ZclCluster> existingClusters, int clusterId,
                Function<Integer, ZclCluster> getClusterById, Consumer<ZclCluster> registrationFunction) {
            if (existingClusters.stream().anyMatch(c -> c.getClusterId().intValue() == clusterId)) {
                // bind to each output cluster only once
                return true;
            }

            ZclCluster cluster = getClusterById.apply(clusterId);
            if (cluster == null) {
                logger.error("{}: Error opening {} cluster {} on endpoint {}", endpoint.getIeeeAddress(), clusterType,
                        clusterId, endpoint.getEndpointId());
                return false;
            }

            try {
                CommandResult bindResponse = bind(cluster).get();
                if (!bindResponse.isSuccess()) {
                    logger.error("{}: Error 0x{} setting {} binding for cluster {}", endpoint.getIeeeAddress(),
                            toHexString(bindResponse.getStatusCode()), clusterType, clusterId);
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("{}: Exception setting {} binding to cluster {}", endpoint.getIeeeAddress(), clusterType,
                        clusterId, e);
            }

            registrationFunction.accept(cluster);
            existingClusters.add(cluster);
            return true;
        }
    }

    protected final class AttributeReportSpec extends EventSpec {
        private final Integer attributeId;
        private final String attributeValue;

        AttributeReportSpec(int clusterId, Integer attributeId, String attributeValue) {
            super(clusterId);
            this.attributeId = attributeId;
            this.attributeValue = attributeValue;
        }

        @Override
        boolean matches(ZclCommand command) {
            return false;
        }

        @Override
        boolean matches(ZclAttribute attribute, Object value) {
            if (attributeId == null) {
                return false;
            }
            boolean attributeIdMatches = attribute.getId() == attributeId;
            boolean attributeValueMatches = Objects.equals(Objects.toString(value), attributeValue);
            return attributeIdMatches && attributeValueMatches;
        }

        @Override
        boolean bindCluster() {
            return bindCluster("server", serverClusters, getClusterId(), endpoint::getInputCluster,
                    cluster -> cluster.addAttributeListener(ZigBeeConverterGenericButton.this));
        }
    }

    private final class CommandSpec extends EventSpec {
        private final Integer commandId;
        private final String commandParameterName;
        private final String commandParameterValue;

        private CommandSpec(int clusterId, Integer commandId, String commandParameterName,
                String commandParameterValue) {
            super(clusterId);
            this.commandId = commandId;
            this.commandParameterName = commandParameterName;
            this.commandParameterValue = commandParameterValue;
        }

        private boolean matchesParameter(ZclCommand command) {
            String capitalizedParameterName = commandParameterName.substring(0, 1).toUpperCase()
                    + commandParameterName.substring(1);
            try {
                Method propertyGetter = command.getClass().getMethod("get" + capitalizedParameterName);
                Object result = propertyGetter.invoke(command);
                return Objects.equals(result.toString(), commandParameterValue);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                logger.warn("{}: Could not read parameter {} for command {}", endpoint.getIeeeAddress(),
                        commandParameterName, command, e);
                return false;
            }
        }

        @Override
        boolean matches(ZclCommand command) {
            if (commandId == null) {
                return false;
            }
            boolean commandIdMatches = command.getCommandId().intValue() == commandId;
            return commandIdMatches
                    && (commandParameterName == null || commandParameterValue == null || matchesParameter(command));
        }

        @Override
        boolean matches(ZclAttribute attribute, Object value) {
            return false;
        }

        @Override
        boolean bindCluster() {
            return bindCluster("client", clientClusters, getClusterId(), endpoint::getOutputCluster,
                    cluster -> cluster.addCommandListener(ZigBeeConverterGenericButton.this));
        }
    }
}
