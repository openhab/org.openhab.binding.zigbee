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
    public Integer zigbee_powermode;
    public Integer zigbee_childtimeout;
    public Integer zigbee_concentrator;
    public Integer zigbee_networksize;
    public String zigbee_groupregistration;
}
