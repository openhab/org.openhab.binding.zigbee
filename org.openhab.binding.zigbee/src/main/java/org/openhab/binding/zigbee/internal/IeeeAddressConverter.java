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
package org.openhab.binding.zigbee.internal;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.zsmartsystems.zigbee.IeeeAddress;

/**
 * Converter for XStream to serialise the {@link IeeeAddress} as a string
 *
 * @author Chris Jackson
 *
 */
public class IeeeAddressConverter implements Converter {
    @Override
    public boolean canConvert(Class clazz) {
        return clazz.equals(IeeeAddress.class);
    }

    @Override
    public void marshal(Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
        IeeeAddress address = (IeeeAddress) value;
        writer.setValue(address.toString());
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        return new IeeeAddress(reader.getValue());
    }

}
