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

package org.bubblecloud.zigbee.api.cluster.impl;

import org.bubblecloud.zigbee.api.ReportingConfiguration;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Reporter;
import org.bubblecloud.zigbee.api.cluster.impl.event.MeasuredValueBridgeListeners;
import org.bubblecloud.zigbee.api.cluster.impl.measurement_sensing.ColorSensorMeasurementCluster;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.ColorSensorMeasurement;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.event.MeasuredValueListener;
import org.bubblecloud.zigbee.network.ZigBeeEndpoint;

/**
 * @author Nathalie Hipp, Hahn-Schickard
 * @author <a href="mailto:giancarlo.riolo@isti.cnr.it">Giancarlo Riolo</a>
 * @version $LastChangedRevision: $ ($LastChangedDate: $)
 *
 */
public class ColorSensorMeasurementImpl implements ColorSensorMeasurement {

    private final ColorSensorMeasurementCluster colorSensorMeasurementCluster;

    private final Attribute measuredValueX;
    private final Attribute measuredValueY;
    private final Attribute measuredValueZ;

    private final MeasuredValueBridgeListeners measureBridge;

    public ColorSensorMeasurementImpl(ZigBeeEndpoint zbDevice) {
        colorSensorMeasurementCluster = new ColorSensorMeasurementCluster(zbDevice);
        measuredValueX = colorSensorMeasurementCluster.getAttributeMeasuredValueX();
        measuredValueY = colorSensorMeasurementCluster.getAttributeMeasuredValueY();
        measuredValueZ = colorSensorMeasurementCluster.getAttributeMeasuredValueZ();

        measureBridge = new MeasuredValueBridgeListeners(new ReportingConfiguration(), measuredValueX, this);
    }

    @Override
    public Attribute getMeasuredValueX() {
        return measuredValueX;
    }

    @Override
    public Attribute getMeasuredValueY() {
        return measuredValueY;
    }

    @Override
    public Attribute getMeasuredValueZ() {
        return measuredValueZ;
    }

    @Override
    public Reporter[] getAttributeReporters() {
        return colorSensorMeasurementCluster.getAttributeReporters();
    }

    @Override
    public int getId() {
        return colorSensorMeasurementCluster.getId();
    }

    @Override
    public String getName() {
        return colorSensorMeasurementCluster.getName();
    }

    @Override
    public Attribute getAttribute(int id) {
        Attribute[] attributes = colorSensorMeasurementCluster.getAvailableAttributes();
        for (int i = 0; i < attributes.length; i++) {
            if (attributes[i].getId() == id) {
                return attributes[i];
            }
        }
        return null;
    }

    @Override
    public Attribute[] getAttributes() {
        return colorSensorMeasurementCluster.getAvailableAttributes();
    }

    @Override
    public boolean subscribe(MeasuredValueListener listener) {
        return measureBridge.subscribe(listener);
    }

    @Override
    public boolean unsubscribe(MeasuredValueListener listener) {
        return measureBridge.unsubscribe(listener);
    }
}