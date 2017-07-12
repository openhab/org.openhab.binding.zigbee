/*
Copyright 2008-2013 CNR-ISTI, http://isti.cnr.it
Institute of Information Science and Technologies
of the Italian National Research Council


See the NOTICE file distributed with this work for additional
information regarding copyright ownership

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.bubblecloud.zigbee.api.cluster.impl.measurement_sensing;

import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.measurement_sensing.PressureMeasurement;
import org.bubblecloud.zigbee.api.cluster.impl.api.measurement_sensing.TemperatureMeasurement;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.bubblecloud.zigbee.api.cluster.impl.core.AttributeImpl;
import org.bubblecloud.zigbee.api.cluster.impl.core.ZCLClusterBase;
import org.bubblecloud.zigbee.network.ZigBeeEndpoint;

/**
 * Implementation of the {@link TemperatureMeasurement} interface
 *
 * @author Nathalie Hipp, Hahn-Schickard
 * @author <a href="mailto:stefano.lenzi@isti.cnr.it">Stefano "Kismet" Lenzi</a>
 * @author <a href="mailto:francesco.furfari@isti.cnr.it">Francesco Furfari</a>
 * @version $LastChangedRevision: 799 $ ($LastChangedDate: 2013-08-06 19:00:05 +0300 (Tue, 06 Aug 2013) $)
 * @since 0.4.0
 */

public class PressureMeasurementCluster extends ZCLClusterBase implements PressureMeasurement {

    /*
     * Ausskommentiert Nathi
     * private static AttributeImpl description;
     */

    // Quellcode Nathi
    private final AttributeImpl measuredValue;
    private final AttributeImpl minMeasuredValue;
    private final AttributeImpl maxMeasuredValue;
    private final AttributeImpl tolerance;
    // Ende Quellcode Nathi

    private final Attribute[] attributes;

    public PressureMeasurementCluster(ZigBeeEndpoint zbDevice) {
        super(zbDevice);

        /*
         * Auskommentiert Nathi
         * description = new AttributeImpl(zbDevice,this, Attributes.DESCRIPTION);
         */

        // Quellcode Nathi
        measuredValue = new AttributeImpl(zbDevice, this, Attributes.MEASURED_VALUE_SIGNED_16_BIT);
        minMeasuredValue = new AttributeImpl(zbDevice, this, Attributes.MIN_MEASURED_VALUE_SIGNED_16_BIT);
        maxMeasuredValue = new AttributeImpl(zbDevice, this, Attributes.MAX_MEASURED_VALUE_SIGNED_16_BIT);
        tolerance = new AttributeImpl(zbDevice, this, Attributes.TOLERANCE);
        // Ende Quellcode Nathi

        /*
         * Auskommentiert Nathi
         * attributes = new AttributeImpl[] { description };
         */

        // Quellcode Nathi
        attributes = new AttributeImpl[] { measuredValue, minMeasuredValue, maxMeasuredValue, tolerance };
        // Ende Quellcode Nathi
    }

    @Override
    public short getId() {
        return PressureMeasurement.ID;
    }

    @Override
    public String getName() {
        return PressureMeasurement.NAME;
    }

    @Override
    public Attribute[] getStandardAttributes() {
        return attributes;
    }

    /*
     * Ausskommentiert Nathi
     * public Attribute getAttributeDescription() {
     * return description;
     * }
     */

    // Quellcode Nathi
    @Override
    public Attribute getAttributeMaxMeasuredValue() {
        return maxMeasuredValue;
    }

    @Override
    public Attribute getAttributeMeasuredValue() {
        return measuredValue;
    }

    @Override
    public Attribute getAttributeMinMeasuredValue() {
        return minMeasuredValue;
    }

    @Override
    public Attribute getAttributeTolerance() {
        return tolerance;
    }
    // Ende Quellcode Nathi

}
