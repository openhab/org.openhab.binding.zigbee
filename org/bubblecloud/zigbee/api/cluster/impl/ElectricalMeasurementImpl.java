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
import org.bubblecloud.zigbee.api.cluster.impl.measurement_sensing.ElectricalMeasurementCluster;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.ElectricalMeasurement;
import org.bubblecloud.zigbee.network.ZigBeeEndpoint;

/**
 * @author Nathalie Hipp, Hahn-Schickard
 * @author <a href="mailto:stefano.lenzi@isti.cnr.it">Stefano "Kismet" Lenzi</a>
 * @author <a href="mailto:francesco.furfari@isti.cnr.it">Francesco Furfari</a>
 * @author <a href="mailto:alessandro.giari@isti.cnr.it">Alessandro Giari</a>
 * @author <a href="mailto:ryan@presslab.us">Ryan Press</a>
 * @version $LastChangedRevision: 799 $ ($LastChangedDate: 2015-06-23 19:00:05 +0300 (Tue, 23 Jun 2015) $)
 * @since 0.1.0
 */
public class ElectricalMeasurementImpl implements ElectricalMeasurement {

    private final ElectricalMeasurementCluster electricalMeasurementCluster;
    private final Attribute measurementType;

    /*
     * Ausskommentiert Nathi
     * private final Attribute acVoltageMultiplier;
     * private final Attribute acVoltageDivisor;
     * private final Attribute acCurrentMultiplier;
     * private final Attribute acCurrentDivisor;
     * private final Attribute acPowerMultiplier;
     * private final Attribute acPowerDivisor;
     */

    // QUellcode Nathi
    private final Attribute dcVoltage;
    private final Attribute dcVoltageMin;
    private final Attribute dcVoltageMax;
    private final Attribute dcCurrent;
    private final Attribute dcCurrentMin;
    private final Attribute dcCurrentMax;
    private final Attribute dcPower;
    private final Attribute dcPowerMin;
    private final Attribute dcPowerMax;

    private final Attribute dcVoltageMultiplier;
    private final Attribute dcVoltageDivisor;
    private final Attribute dcCurrentMultiplier;
    private final Attribute dcCurrentDivisor;
    private final Attribute dcPowerMultiplier;
    private final Attribute dcPowerDivisor;

    private final MeasuredValueBridgeListeners measureBridgeVoltage;
    private final MeasuredValueBridgeListeners measureBridgeCurrent;
    private final MeasuredValueBridgeListeners measureBridgePower;

    // Ende Quellcode Nathi

    public ElectricalMeasurementImpl(ZigBeeEndpoint zbDevice) {
        electricalMeasurementCluster = new ElectricalMeasurementCluster(zbDevice);
        measurementType = electricalMeasurementCluster.getAttributeMeasurementType();

        // Quellcode Nathi
        dcVoltage = electricalMeasurementCluster.getAttributeDCVoltage();
        dcVoltageMin = electricalMeasurementCluster.getAttributeDCVoltageMax();
        dcVoltageMax = electricalMeasurementCluster.getAttributeDCVoltageMin();
        dcCurrent = electricalMeasurementCluster.getAttributeDCCurrent();
        dcCurrentMin = electricalMeasurementCluster.getAttributeDCCurrentMin();
        dcCurrentMax = electricalMeasurementCluster.getAttributeDCCurrentMax();
        dcPower = electricalMeasurementCluster.getAttributeDCPower();
        dcPowerMin = electricalMeasurementCluster.getAttributeDCPowerMin();
        dcPowerMax = electricalMeasurementCluster.getAttributeDCPowereMax();

        dcVoltageMultiplier = electricalMeasurementCluster.getAttributeDCVoltageMultiplier();
        dcVoltageDivisor = electricalMeasurementCluster.getAttributeDCVoltageDivisor();
        dcCurrentMultiplier = electricalMeasurementCluster.getAttributeDCCurrentMultiplier();
        dcCurrentDivisor = electricalMeasurementCluster.getAttributeDCCurrentDivisor();
        dcPowerMultiplier = electricalMeasurementCluster.getAttributeDCPowerMultiplier();
        dcPowerDivisor = electricalMeasurementCluster.getAttributeDCPowerDivisor();

        measureBridgeVoltage = new MeasuredValueBridgeListeners(new ReportingConfiguration(), dcVoltage, this);
        measureBridgeCurrent = new MeasuredValueBridgeListeners(new ReportingConfiguration(), dcCurrent, this);
        measureBridgePower = new MeasuredValueBridgeListeners(new ReportingConfiguration(), dcPower, this);

        // Ende Nathi

        /*
         * Ausskomentiert Nathi
         * acVoltageMultiplier = electricalMeasurementCluster.getAttributeAcVoltageMultiplier();
         * acVoltageDivisor = electricalMeasurementCluster.getAttributeAcVoltageDivisor();
         * acCurrentMultiplier = electricalMeasurementCluster.getAttributeAcCurrentMultiplier();
         * acCurrentDivisor = electricalMeasurementCluster.getAttributeAcCurrentDivisor();
         * acPowerMultiplier = electricalMeasurementCluster.getAttributeAcPowerMultiplier();
         * acPowerDivisor = electricalMeasurementCluster.getAttributeAcPowerDivisor();
         */

    }

    // Quellcode Nathi

    @Override
    public Attribute getDCVoltage() {
        // TODO Auto-generated method stub
        return dcVoltage;
    }

    @Override
    public Attribute getDCVoltageMin() {
        // TODO Auto-generated method stub
        return dcVoltageMin;
    }

    @Override
    public Attribute getDCVoltageMax() {
        // TODO Auto-generated method stub
        return dcVoltageMax;
    }

    @Override
    public Attribute getDCCurrent() {
        // TODO Auto-generated method stub
        return dcCurrent;
    }

    @Override
    public Attribute getDCCurrentMin() {
        // TODO Auto-generated method stub
        return dcCurrentMin;
    }

    @Override
    public Attribute getDCCurrentMax() {
        // TODO Auto-generated method stub
        return dcCurrentMax;
    }

    @Override
    public Attribute getDCPower() {
        // TODO Auto-generated method stub
        return dcPower;
    }

    @Override
    public Attribute getDCPowerMin() {
        // TODO Auto-generated method stub
        return dcPowerMin;
    }

    @Override
    public Attribute getDCPowerMax() {
        // TODO Auto-generated method stub
        return dcPowerMax;
    }

    // Ende Quellcode Nathi

    /*
     * Ausskomentiert Nathi
     *
     * @Override
     * public Float getAcFrequency() throws ZigBeeDeviceException {
     * try {
     * return (Float) electricalMeasurementCluster.getAttributeAcFrequency().getValue();
     * } catch (ZigBeeClusterException e) {
     * throw new ZigBeeDeviceException(e);
     * }
     * }
     *
     * @Override
     * public Float getRmsVoltage() throws ZigBeeDeviceException {
     * try {
     * Float value = Float.valueOf((Integer) electricalMeasurementCluster.getAttributeRmsVoltage().getValue());
     * try {
     * return value * Float.valueOf((Integer) acVoltageMultiplier.getValue())
     * / Float.valueOf((Integer) acVoltageDivisor.getValue());
     * } catch (ZigBeeClusterException e) {
     * // Scaling is unsupported
     * return value;
     * }
     * } catch (ZigBeeClusterException e) {
     * throw new ZigBeeDeviceException(e);
     * }
     * }
     *
     * @Override
     * public Float getRmsCurrent() throws ZigBeeDeviceException {
     * try {
     * Float value = Float.valueOf((Integer) electricalMeasurementCluster.getAttributeRmsCurrent().getValue());
     * try {
     * return value * Float.valueOf((Integer) acCurrentMultiplier.getValue())
     * / Float.valueOf((Integer) acCurrentDivisor.getValue());
     * } catch (ZigBeeClusterException e) {
     * // Scaling unsupported
     * return value;
     * }
     * } catch (ZigBeeClusterException f) {
     * throw new ZigBeeDeviceException(f);
     * }
     * }
     *
     * @Override
     * public Float getActivePower() throws ZigBeeDeviceException {
     * try {
     * Float value = Float.valueOf((Integer) electricalMeasurementCluster.getAttributeActivePower().getValue());
     * try {
     * return value * Float.valueOf((Integer) acPowerMultiplier.getValue())
     * / Float.valueOf((Integer) acPowerDivisor.getValue());
     * } catch (ZigBeeClusterException e) {
     * // Scaling unsupported
     * return value;
     * }
     * } catch (ZigBeeClusterException f) {
     * throw new ZigBeeDeviceException(f);
     * }
     * }
     *
     * @Override
     * public Float getReactivePower() throws ZigBeeDeviceException {
     * try {
     * Float value = Float.valueOf((Integer) electricalMeasurementCluster.getAttributeReactivePower().getValue());
     * try {
     * return value * Float.valueOf((Integer) acPowerMultiplier.getValue())
     * / Float.valueOf((Integer) acPowerDivisor.getValue());
     * } catch (ZigBeeClusterException e) {
     * // Scaling unsupported
     * return value;
     * }
     * } catch (ZigBeeClusterException f) {
     * throw new ZigBeeDeviceException(f);
     * }
     * }
     *
     * @Override
     * public Float getApparentPower() throws ZigBeeDeviceException {
     * try {
     * Float value = Float.valueOf((Integer) electricalMeasurementCluster.getAttributeApparentPower().getValue());
     * try {
     * return value * Float.valueOf((Integer) acPowerMultiplier.getValue())
     * / Float.valueOf((Integer) acPowerDivisor.getValue());
     * } catch (ZigBeeClusterException e) {
     * // Scaling unsupported
     * return value;
     * }
     * } catch (ZigBeeClusterException f) {
     * throw new ZigBeeDeviceException(f);
     * }
     * }
     *
     * @Override
     * public Float getPowerFactor() throws ZigBeeDeviceException {
     * try {
     * return Float.valueOf((Integer) electricalMeasurementCluster.getAttributePowerFactor().getValue()) / 100;
     * } catch (ZigBeeClusterException e) {
     * throw new ZigBeeDeviceException(e);
     * }
     * }
     */

    @Override
    public Reporter[] getAttributeReporters() {
        return electricalMeasurementCluster.getAttributeReporters();
    }

    @Override
    public int getId() {
        return electricalMeasurementCluster.getId();
    }

    @Override
    public String getName() {
        return electricalMeasurementCluster.getName();
    }

    @Override
    public Attribute getAttribute(int id) {
        Attribute[] attributes = electricalMeasurementCluster.getAvailableAttributes();
        for (int i = 0; i < attributes.length; i++) {
            if (attributes[i].getId() == id) {
                return attributes[i];
            }
        }
        return null;
    }

    @Override
    public Attribute[] getAttributes() {
        return electricalMeasurementCluster.getAvailableAttributes();
    }

}
