/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.warningdevice;

import java.util.Map;

/**
 * Interface for providers of additional warning device configurations that are provided as command options for warning
 * device channels.
 *
 * @author Henning Sudbrock - initial contribution
 */
public interface WarningTypeCommandDescriptionProvider {

    /**
     * @return A map mapping labels for warning command descriptions (to be used by UIs) to {@link WarningType}s.
     */
    Map<String, WarningType> getWarningTypes();

}
