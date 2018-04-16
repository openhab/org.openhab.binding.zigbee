/**
 * Copyright (c) 2014-2018 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.config;

import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.Configuration;

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
     * @return the list of {@link ConfigDescriptionParameter}
     */
    public List<ConfigDescriptionParameter> getConfiguration();

    /**
     * Processes the updated configuration. As required, the method shall process each known configuration parameter and
     * set a local variable for local parameters, and update the remote device for remote parameters.
     *
     * @param configuration the {@link Configuration} to be processed
     * @return the {@link Configuration} to be persisted within the thing
     */
    public Configuration updateConfiguration(@NonNull Configuration configuration);
}
