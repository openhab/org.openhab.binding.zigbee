/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter;

import static java.lang.Integer.*;
import static java.lang.String.format;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.CommonTriggerEvents;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.converter.ZigBeeBaseChannelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;

/**
 * Generic converter for buttons (e.g., from remote controls).
 * <p>
 * This converter needs to be configured with the ZigBee commands that are triggered by the button presses. This is done
 * by channel properties that specify the endpoint, the cluster, the command ID, and (optionally) a command parameter.
 * <p>
 * As the configuration is done via channel properties, this converter is usable via static thing types only.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class ZigBeeConverterGenericButton extends ZigBeeBaseChannelConverter implements ZclCommandListener {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String CLUSTER = "cluster_id";
    private static final String COMMAND = "command_id";
    private static final String PARAM_NAME = "parameter_name";
    private static final String PARAM_VALUE = "parameter_value";

    private Map<ButtonPressType, CommandSpec> handledCommands = new HashMap<>();
    private Set<ZclCluster> clientClusters = new HashSet<>();

    @Override
    public synchronized boolean initializeConverter() {
        for (ButtonPressType buttonPressType : ButtonPressType.values()) {
            CommandSpec commandSpec = parseCommandSpec(buttonPressType);
            if (commandSpec != null) {
                handledCommands.put(buttonPressType, commandSpec);
            }
        }

        if (handledCommands.isEmpty()) {
            logger.error("{}: No command is specified for any of the possible button press types in channel {}",
                    endpoint.getIeeeAddress(), channel.getUID());
            return false;
        }

        for (CommandSpec commandSpec : handledCommands.values()) {
            int clusterId = commandSpec.getClusterId();

            if (clientClusters.stream().anyMatch(cluster -> cluster.getClusterId().intValue() == clusterId)) {
                // bind to each output cluster only once
                continue;
            }

            ZclCluster clientCluster = endpoint.getOutputCluster(clusterId);
            if (clientCluster == null) {
                logger.error("{}: Error opening client cluster {} on endpoint {}", endpoint.getIeeeAddress(), clusterId,
                        endpoint.getEndpointId());
                return false;
            }

            try {
                CommandResult bindResponse = bind(clientCluster).get();
                if (!bindResponse.isSuccess()) {
                    logger.error("{}: Error 0x{} setting client binding for cluster {}", endpoint.getIeeeAddress(),
                            toHexString(bindResponse.getStatusCode()), clusterId);
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error(endpoint.getIeeeAddress() + ": Exception setting binding to cluster " + clusterId, e);
            }

            clientCluster.addCommandListener(this);
            clientClusters.add(clientCluster);
        }

        return true;
    }

    @Override
    public void disposeConverter() {
        for (ZclCluster clientCluster : clientClusters) {
            logger.debug("{}: Closing cluster {}", endpoint.getIeeeAddress(), clientCluster.getClusterId());
            clientCluster.removeCommandListener(this);
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
    public void commandReceived(ZclCommand command) {
        ButtonPressType buttonPressType = getButtonPressType(command);
        if (buttonPressType != null) {
            logger.debug("{}: Matching ZigBee command for press type {} received: {}", endpoint.getIeeeAddress(),
                    buttonPressType, command);
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

    private ButtonPressType getButtonPressType(ZclCommand command) {
        for (Entry<ButtonPressType, CommandSpec> entry : handledCommands.entrySet()) {
            if (entry.getValue().matchesCommand(command)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private CommandSpec parseCommandSpec(ButtonPressType pressType) {
        String clusterProperty = channel.getProperties().get(getParameterName(CLUSTER, pressType));
        String commandProperty = channel.getProperties().get(getParameterName(COMMAND, pressType));
        String commandParameterName = channel.getProperties().get(getParameterName(PARAM_NAME, pressType));
        String commandParameterValue = channel.getProperties().get(getParameterName(PARAM_VALUE, pressType));

        if (clusterProperty == null || commandProperty == null) {
            return null;
        }

        int clusterId;
        int commandId;

        try {
            clusterId = parseId(clusterProperty);
        } catch (NumberFormatException e) {
            logger.warn("{}: Could not parse cluster property {}", endpoint.getIeeeAddress(), clusterProperty);
            return null;
        }

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

    private String getParameterName(String parameterType, ButtonPressType buttonPressType) {
        return String.format("zigbee_%s_%s", buttonPressType, parameterType);
    }

    private int parseId(String id) throws NumberFormatException {
        if (id.startsWith("0x")) {
            return parseInt(id.substring(2, id.length()), 16);
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

    private class CommandSpec {
        private final int clusterId;
        private final int commandId;
        private final String commandParameterName;
        private final String commandParameterValue;

        public CommandSpec(int clusterId, int commandId, String commandParameterName, String commandParameterValue) {
            this.clusterId = clusterId;
            this.commandId = commandId;
            this.commandParameterName = commandParameterName;
            this.commandParameterValue = commandParameterValue;
        }

        public boolean matchesCommand(ZclCommand command) {
            boolean commandIdMatches = command.getCommandId().intValue() == commandId;
            boolean commandParameterMatches = commandParameterName == null || commandParameterValue == null
                    || matchesParameter(command);

            return commandIdMatches && commandParameterMatches;
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
                logger.warn(format("%s: Could not read parameter %s for command %s", endpoint.getIeeeAddress(),
                        commandParameterName, command), e);
                return false;
            }
        }

        public int getClusterId() {
            return clusterId;
        }
    }
}
