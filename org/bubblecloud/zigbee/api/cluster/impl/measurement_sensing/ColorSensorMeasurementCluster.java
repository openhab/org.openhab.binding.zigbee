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
import org.bubblecloud.zigbee.api.cluster.impl.api.measurement_sensing.ColorSensorMeasurement;
import org.bubblecloud.zigbee.api.cluster.impl.api.measurement_sensing.TemperatureMeasurement;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.bubblecloud.zigbee.api.cluster.impl.core.AttributeImpl;
import org.bubblecloud.zigbee.api.cluster.impl.core.ZCLClusterBase;
import org.bubblecloud.zigbee.api.device.generic.SimpleSensor;
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

public class ColorSensorMeasurementCluster extends ZCLClusterBase implements ColorSensorMeasurement {

    /*
     * Ausskommentiert Nathi
     * private static AttributeImpl description;
     */

    // Quellcode Nathi
    private final AttributeImpl measuredValueX;
    private final AttributeImpl measuredValueY;
    private final AttributeImpl measuredValueZ;
    // Ende Quellcode Nathi

    private final Attribute[] attributes;

    public ColorSensorMeasurementCluster(ZigBeeEndpoint zbDevice) {
        super(zbDevice);

        // Quellcode Nathi
        measuredValueX = new AttributeImpl(zbDevice, this, Attributes.MEASURED_VALUE_X);
        measuredValueY = new AttributeImpl(zbDevice, this, Attributes.MEASURED_VALUE_Y);
        measuredValueZ = new AttributeImpl(zbDevice, this, Attributes.MEASURED_VALUE_Z);
        // Ende Quellcode Nathi

        // Quellcode Nathi
        attributes = new AttributeImpl[] { measuredValueX, measuredValueY, measuredValueZ };
        // Ende Quellcode Nathi
    }

    @Override
    public short getId() {
        return ColorSensorMeasurement.ID;
    }

    @Override
    public String getName() {
        return SimpleSensor.NAME;
    }

    @Override
    public Attribute[] getStandardAttributes() {
        return attributes;
    }

    @Override
    public Attribute getAttributeMeasuredValueX() {
        // TODO Auto-generated method stub
        return measuredValueX;
    }

    @Override
    public Attribute getAttributeMeasuredValueY() {
        // TODO Auto-generated method stub
        return measuredValueY;
    }

    @Override
    public Attribute getAttributeMeasuredValueZ() {
        // TODO Auto-generated method stub
        return measuredValueZ;
    }

}
