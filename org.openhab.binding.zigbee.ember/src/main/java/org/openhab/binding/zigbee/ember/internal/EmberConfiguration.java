/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.ember.internal;

/**
 * The {@link EmberConfiguration} class contains fields mapping thing configuration
 * parameters.
 *
 * @author Chris Jackson - Initial contribution
 */
public class EmberConfiguration {
    public String zigbee_port;
    public Integer zigbee_baud;
    public Integer zigbee_flowcontrol;
}
