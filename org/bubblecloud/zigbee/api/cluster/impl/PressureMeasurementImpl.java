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
import org.bubblecloud.zigbee.api.cluster.impl.event.ToleranceBridgeListeners;
import org.bubblecloud.zigbee.api.cluster.impl.measurement_sensing.PressureMeasurementCluster;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.PressureMeasurement;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.event.MeasuredValueListener;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.event.ToleranceListener;
import org.bubblecloud.zigbee.network.ZigBeeEndpoint;

/**
 * @author Nathalie Hipp, Hahn-Schickard
 * @author <a href="mailto:giancarlo.riolo@isti.cnr.it">Giancarlo Riolo</a>
 * @version $LastChangedRevision: $ ($LastChangedDate: $)
 *
 */
public class PressureMeasurementImpl implements PressureMeasurement {

    private final PressureMeasurementCluster pressureMeasurementCluster;

    // Quellcode Nathi
    private final Attribute measuredValue;
    private final Attribute minMeasuredValue;
    private final Attribute maxMeasuredValue;
    private final Attribute tolerance;

    private final MeasuredValueBridgeListeners measureBridge;
    private final ToleranceBridgeListeners toleranceBridge;
    // Ende Quellcode Nathi

    public PressureMeasurementImpl(ZigBeeEndpoint zbDevice) {
        pressureMeasurementCluster = new PressureMeasurementCluster(zbDevice);

        // Quellcode Nathi
        measuredValue = pressureMeasurementCluster.getAttributeMeasuredValue();
        minMeasuredValue = pressureMeasurementCluster.getAttributeMinMeasuredValue();
        maxMeasuredValue = pressureMeasurementCluster.getAttributeMaxMeasuredValue();
        tolerance = pressureMeasurementCluster.getAttributeTolerance();

        toleranceBridge = new ToleranceBridgeListeners(new ReportingConfiguration(), tolerance, this);
        measureBridge = new MeasuredValueBridgeListeners(new ReportingConfiguration(), measuredValue, this);
        // Ende Quellcode Nathi

    }

    @Override
    public int getId() {

        return pressureMeasurementCluster.getId();
    }

    @Override
    public String getName() {

        return pressureMeasurementCluster.getName();
    }

    @Override
    public Reporter[] getAttributeReporters() {
        return pressureMeasurementCluster.getAttributeReporters();
    }

    @Override
    public Attribute[] getAttributes() {

        return pressureMeasurementCluster.getAvailableAttributes();
    }

    @Override
    public Attribute getAttribute(int id) {
        Attribute[] attributes = pressureMeasurementCluster.getAvailableAttributes();
        for (int i = 0; i < attributes.length; i++) {
            if (attributes[i].getId() == id) {
                return attributes[i];
            }
        }
        return null;
    }

    /*
     * Ausskomentiert Nathi
     * public String getDescription() throws ZigBeeDeviceException {
     * try {
     * return (String) pressureMeasurementCluster.getAttributeDescription().getValue();
     * } catch (ZigBeeClusterException e) {
     * throw new ZigBeeDeviceException(e);
     * }
     * }
     */

    // Quellcode Nathi
    @Override
    public Attribute getMaxMeasuredValue() {
        return maxMeasuredValue;
    }

    @Override
    public Attribute getMeasuredValue() {
        return measuredValue;
    }

    @Override
    public Attribute getMinMeasuredValue() {
        return minMeasuredValue;
    }

    @Override
    public Attribute getTolerance() {
        return tolerance;
    }

    @Override
    public boolean subscribe(MeasuredValueListener listener) {
        return measureBridge.subscribe(listener);
    }

    @Override
    public boolean subscribe(ToleranceListener listener) {
        return toleranceBridge.subscribe(listener);
    }

    @Override
    public boolean unsubscribe(MeasuredValueListener listener) {
        return measureBridge.unsubscribe(listener);
    }

    @Override
    public boolean unsubscribe(ToleranceListener listener) {
        return toleranceBridge.unsubscribe(listener);
    }
    // Ende Quellcode Nathi
}