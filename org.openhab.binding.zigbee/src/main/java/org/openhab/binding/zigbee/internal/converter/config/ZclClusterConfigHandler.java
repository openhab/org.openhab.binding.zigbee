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
package org.openhab.binding.zigbee.internal.converter.config;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.Configuration;

import com.zsmartsystems.zigbee.zcl.ZclCluster;

/**
 * Base configuration handler for the {@link ZclCluster}.
 * <p>
 * The configuration handler provides configuration services for a cluster. The handler supports the discovery of
 * configuration attributes within the remote device, and the generation of the ESH configuration descriptions. It is
 * then able to process configuration updates, and sends the attribute updates to the device. It provides a getter for
 * any local configuration parameters (ie those that are not attributes in the remote device).
 *
 * @author Chris Jackson
 *
 */
public interface ZclClusterConfigHandler {
    /**
     * Creates the list of {@link ConfigDescriptionParameter}. This method shall check the available attributes on the
     * remote device and create configuration parameters for each supported attribute that needs to be configurable.
     *
     * @param cluster the {@link ZclCluster} to get the configuration
     * @return true if this cluster has configuration descriptions
     */
    boolean initialize(ZclCluster cluster);

    /**
     * Gets the configuration description that was generated dynamically
     *
     * @return the list of {@link ConfigDescriptionParameter}
     */
    List<ConfigDescriptionParameter> getConfiguration();

    /**
     * Processes the updated configuration. As required, the method shall process each known configuration parameter and
     * set a local variable for local parameters, and update the remote device for remote parameters.
     * The currentConfiguration shall be updated.
     *
     * @param currentConfiguration the current {@link Configuration}
     * @param updatedParameters a map containing the updated configuration parameters to be set
     * @return true if the configuration was updated
     */
    boolean updateConfiguration(@NonNull Configuration currentConfiguration, Map<String, Object> updatedParameters);
}
