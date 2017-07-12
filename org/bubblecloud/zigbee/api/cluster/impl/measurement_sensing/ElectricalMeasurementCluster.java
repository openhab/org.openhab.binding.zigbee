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
import org.bubblecloud.zigbee.api.cluster.impl.api.measurement_sensing.ElectricalMeasurement;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.bubblecloud.zigbee.api.cluster.impl.core.AttributeImpl;
import org.bubblecloud.zigbee.api.cluster.impl.core.ZCLClusterBase;
import org.bubblecloud.zigbee.network.ZigBeeEndpoint;

/**
 * Implementation of the {@link ElectricalMeasurement} interface
 *
 * @author Nathalie Hipp, Hahn-Schickard
 * @author <a href="mailto:stefano.lenzi@isti.cnr.it">Stefano "Kismet" Lenzi</a>
 * @author <a href="mailto:francesco.furfari@isti.cnr.it">Francesco Furfari</a>
 * @author <a href="mailto:ryan@presslab.us">Ryan Press</a>
 * @version $LastChangedRevision: 799 $ ($LastChangedDate: 2015-06-23 19:00:05 +0300 (Tue, 23 Jun 2015) $)
 * @since 0.4.0
 */
public class ElectricalMeasurementCluster extends ZCLClusterBase implements ElectricalMeasurement {

    private final AttributeImpl measurementType;

    // Quellcode Nathi

    private final AttributeImpl dcVoltage;
    private final AttributeImpl dcVoltageMin;
    private final AttributeImpl dcVoltageMax;
    private final AttributeImpl dcCurrent;
    private final AttributeImpl dcCurrentMin;
    private final AttributeImpl dcCurrentMax;
    private final AttributeImpl dcPower;
    private final AttributeImpl dcPowerMin;
    private final AttributeImpl dcPowerMax;
    private final AttributeImpl dcVoltageMultiplier;
    private final AttributeImpl dcVoltageDivisor;
    private final AttributeImpl dcCurrentMultiplier;
    private final AttributeImpl dcCurrentDivisor;
    private final AttributeImpl dcPowerMultiplier;
    private final AttributeImpl dcPowerDivisor;

    // Ende Nathi

    /*
     * Ausskomewntiert Nathi
     * private final AttributeImpl acFrequency;
     *
     * private final AttributeImpl rmsVoltage;
     * private final AttributeImpl rmsCurrent;
     * private final AttributeImpl activePower;
     * private final AttributeImpl reactivePower;
     * private final AttributeImpl apparentPower;
     * private final AttributeImpl powerFactor;
     * private final AttributeImpl acVoltageMultiplier;
     * private final AttributeImpl acVoltageDivisor;
     * private final AttributeImpl acCurrentMultiplier;
     * private final AttributeImpl acCurrentDivisor;
     * private final AttributeImpl acPowerMultiplier;
     * private final AttributeImpl acPowerDivisor;
     */

    private final Attribute[] attributes;

    public ElectricalMeasurementCluster(ZigBeeEndpoint zbDevice) {
        super(zbDevice);
        measurementType = new AttributeImpl(zbDevice, this, Attributes.MEASUREMENT_TYPE);

        /*
         * Ausskommentiert Nathi
         * acFrequency = new AttributeImpl(zbDevice, this, Attributes.AC_FREQUENCY);
         *
         * rmsVoltage = new AttributeImpl(zbDevice, this, Attributes.RMS_VOLTAGE);
         * rmsCurrent = new AttributeImpl(zbDevice, this, Attributes.RMS_CURRENT);
         * activePower = new AttributeImpl(zbDevice, this, Attributes.ACTIVE_POWER);
         * reactivePower = new AttributeImpl(zbDevice, this, Attributes.REACTIVE_POWER);
         * apparentPower = new AttributeImpl(zbDevice, this, Attributes.APPARENT_POWER);
         * powerFactor = new AttributeImpl(zbDevice, this, Attributes.POWER_FACTOR);
         * acVoltageMultiplier = new AttributeImpl(zbDevice, this, Attributes.AC_VOLTAGE_MULTIPLIER);
         * acVoltageDivisor = new AttributeImpl(zbDevice, this, Attributes.AC_VOLTAGE_DIVISOR);
         * acCurrentMultiplier = new AttributeImpl(zbDevice, this, Attributes.AC_CURRENT_MULTIPLIER);
         * acCurrentDivisor = new AttributeImpl(zbDevice, this, Attributes.AC_CURRENT_DIVISOR);
         * acPowerMultiplier = new AttributeImpl(zbDevice, this, Attributes.AC_POWER_MULTIPLIER);
         * acPowerDivisor = new AttributeImpl(zbDevice, this, Attributes.AC_POWER_DIVISOR);
         *
         * attributes = new AttributeImpl[] { measurementType, acFrequency, rmsVoltage, rmsCurrent, activePower,
         * reactivePower, apparentPower, powerFactor, acVoltageMultiplier, acVoltageDivisor, acCurrentMultiplier,
         * acCurrentDivisor, acPowerMultiplier, acPowerDivisor };
         */

        // Quellcode Nathi
        dcVoltage = new AttributeImpl(zbDevice, this, Attributes.DC_VOLTAGE);
        dcVoltageMin = new AttributeImpl(zbDevice, this, Attributes.DC_VOLTAGE_MIN);
        dcVoltageMax = new AttributeImpl(zbDevice, this, Attributes.DC_VOLTAGE_MAX);
        dcCurrent = new AttributeImpl(zbDevice, this, Attributes.DC_CURRENT);
        dcCurrentMin = new AttributeImpl(zbDevice, this, Attributes.DC_CURRENT_MIN);
        dcCurrentMax = new AttributeImpl(zbDevice, this, Attributes.DC_CURRENT_MAX);
        dcPower = new AttributeImpl(zbDevice, this, Attributes.DC_POWER);
        dcPowerMin = new AttributeImpl(zbDevice, this, Attributes.DC_POWER_MIN);
        dcPowerMax = new AttributeImpl(zbDevice, this, Attributes.DC_POWER_MAX);
        dcVoltageMultiplier = new AttributeImpl(zbDevice, this, Attributes.DC_VOLTAGE_MULTIPLIER);
        dcVoltageDivisor = new AttributeImpl(zbDevice, this, Attributes.DC_VOLTAGE_DIVISOR);
        dcCurrentMultiplier = new AttributeImpl(zbDevice, this, Attributes.DC_CURRENT_MULTIPLIER);
        dcCurrentDivisor = new AttributeImpl(zbDevice, this, Attributes.DC_CURRENT_DIVISOR);
        dcPowerMultiplier = new AttributeImpl(zbDevice, this, Attributes.DC_POWER_MULTIPLIER);
        dcPowerDivisor = new AttributeImpl(zbDevice, this, Attributes.DC_POWER_DIVISOR);

        attributes = new AttributeImpl[] { measurementType, dcVoltage, dcVoltageMin, dcVoltageMax, dcCurrent,
                dcCurrentMin, dcCurrentMax, dcPower, dcPowerMin, dcPowerMax, dcVoltageMultiplier, dcVoltageDivisor,
                dcCurrentMultiplier, dcCurrentDivisor, dcPowerMultiplier, dcPowerDivisor };
        // Ende QUellcode Nathi
    }

    @Override
    public short getId() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Attribute[] getStandardAttributes() {
        return attributes;
    }

    @Override
    public Attribute getAttributeMeasurementType() {
        return measurementType;
    }

    // Quellcode Nathi
    @Override
    public Attribute getAttributeDCVoltage() {
        // TODO Auto-generated method stub
        return dcVoltage;
    }

    @Override
    public Attribute getAttributeDCVoltageMin() {
        // TODO Auto-generated method stub
        return dcVoltageMin;
    }

    @Override
    public Attribute getAttributeDCVoltageMax() {
        // TODO Auto-generated method stub
        return dcVoltageMax;
    }

    @Override
    public Attribute getAttributeDCCurrent() {
        // TODO Auto-generated method stub
        return dcCurrent;
    }

    @Override
    public Attribute getAttributeDCCurrentMin() {
        // TODO Auto-generated method stub
        return dcCurrentMin;
    }

    @Override
    public Attribute getAttributeDCCurrentMax() {
        // TODO Auto-generated method stub
        return dcCurrentMax;
    }

    @Override
    public Attribute getAttributeDCPower() {
        // TODO Auto-generated method stub
        return dcPower;
    }

    @Override
    public Attribute getAttributeDCPowerMin() {
        // TODO Auto-generated method stub
        return dcPowerMin;
    }

    @Override
    public Attribute getAttributeDCPowereMax() {
        // TODO Auto-generated method stub
        return dcPowerMax;
    }

    @Override
    public Attribute getAttributeDCVoltageMultiplier() {
        // TODO Auto-generated method stub
        return dcVoltageMultiplier;
    }

    @Override
    public Attribute getAttributeDCVoltageDivisor() {
        // TODO Auto-generated method stub
        return dcVoltageDivisor;
    }

    @Override
    public Attribute getAttributeDCCurrentMultiplier() {
        // TODO Auto-generated method stub
        return dcCurrentMultiplier;
    }

    @Override
    public Attribute getAttributeDCCurrentDivisor() {
        // TODO Auto-generated method stub
        return dcCurrentDivisor;
    }

    @Override
    public Attribute getAttributeDCPowerMultiplier() {
        // TODO Auto-generated method stub
        return dcPowerMultiplier;
    }

    @Override
    public Attribute getAttributeDCPowerDivisor() {
        // TODO Auto-generated method stub
        return dcPowerDivisor;
    }
    // Ende Nathi

    /*
     * Auskommentiert Nathi
     * public Attribute getAttributeAcFrequency() {
     * return acFrequency;
     * }
     *
     * public Attribute getAttributeRmsVoltage() {
     * return rmsVoltage;
     * }
     *
     * public Attribute getAttributeRmsCurrent() {
     * return rmsCurrent;
     * }
     *
     * public Attribute getAttributeActivePower() {
     * return activePower;
     * }
     *
     * public Attribute getAttributeReactivePower() {
     * return reactivePower;
     * }
     *
     * public Attribute getAttributeApparentPower() {
     * return apparentPower;
     * }
     *
     * public Attribute getAttributePowerFactor() {
     * return powerFactor;
     * }
     *
     * public Attribute getAttributeAcVoltageMultiplier() {
     * return acVoltageMultiplier;
     * }
     *
     * public Attribute getAttributeAcVoltageDivisor() {
     * return acVoltageDivisor;
     * }
     *
     * public Attribute getAttributeAcCurrentMultiplier() {
     * return acCurrentMultiplier;
     * }
     *
     * public Attribute getAttributeAcCurrentDivisor() {
     * return acCurrentDivisor;
     * }
     *
     * public Attribute getAttributeAcPowerMultiplier() {
     * return acPowerMultiplier;
     * }
     *
     * public Attribute getAttributeAcPowerDivisor() {
     * return acPowerDivisor;
     * }
     */

}
