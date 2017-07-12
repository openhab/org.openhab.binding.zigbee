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
import org.bubblecloud.zigbee.api.ZigBeeDeviceException;
import org.bubblecloud.zigbee.api.cluster.general.AnalogInput;
import org.bubblecloud.zigbee.api.cluster.general.event.PresentValueListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Reporter;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.event.PresentValueBridgeListeners;
import org.bubblecloud.zigbee.api.cluster.impl.general.AnalogInputCluster;
import org.bubblecloud.zigbee.network.ZigBeeEndpoint;

/**
 *
 * @author <a href="mailto:giancarlo.riolo@isti.cnr.it">Giancarlo Riolo</a>
 * @version $LastChangedRevision: $ ($LastChangedDate: $)
 *
 */

public class AnalogInputImpl implements AnalogInput {
    private final Attribute presentValue;
    private final Attribute outOfService;
    private final Attribute statusFlags;

    // Quellcode Nathi

    private final Attribute valueX;
    private final Attribute valueY;
    private final Attribute valueZ;

    private PresentValueBridgeListeners eventBridgeX;
    private PresentValueBridgeListeners eventBridgeY;
    private PresentValueBridgeListeners eventBridgeZ;

    // Ende Nathi

    private AnalogInputCluster analogInputCluster;
    private PresentValueBridgeListeners eventBridge;

    public AnalogInputImpl(ZigBeeEndpoint zbDevice) {
        analogInputCluster = new AnalogInputCluster(zbDevice);
        presentValue = analogInputCluster.getAttributePresentValue();
        outOfService = analogInputCluster.getAttributeOutOfService();
        statusFlags = analogInputCluster.getAttributeStatusFlags();

        // Quellcode Nathi

        valueX = analogInputCluster.getAttributeValueX();
        valueY = analogInputCluster.getAttributeValueY();
        valueZ = analogInputCluster.getAttributeValueZ();

        eventBridgeX = new PresentValueBridgeListeners(new ReportingConfiguration(), valueX, this);
        eventBridgeY = new PresentValueBridgeListeners(new ReportingConfiguration(), valueY, this);
        eventBridgeZ = new PresentValueBridgeListeners(new ReportingConfiguration(), valueZ, this);

        // Ende Nathi

        eventBridge = new PresentValueBridgeListeners(new ReportingConfiguration(), presentValue, this);
    }

    @Override
    public boolean subscribe(PresentValueListener listener) {
        return eventBridge.subscribe(listener);
    }

    @Override
    public boolean unsubscribe(PresentValueListener listener) {
        return eventBridge.unsubscribe(listener);
    }

    @Override
    public Reporter[] getAttributeReporters() {
        return analogInputCluster.getAttributeReporters();
    }

    @Override
    public int getId() {
        return analogInputCluster.getId();
    }

    @Override
    public String getName() {
        return analogInputCluster.getName();
    }

    @Override
    public Attribute getAttribute(int id) {
        Attribute[] attributes = analogInputCluster.getAvailableAttributes();
        for (int i = 0; i < attributes.length; i++) {
            if (attributes[i].getId() == id) {
                return attributes[i];
            }
        }
        return null;
    }

    @Override
    public Attribute[] getAttributes() {
        return analogInputCluster.getAvailableAttributes();
    }

    @Override
    public String getDescription() throws ZigBeeDeviceException {
        try {
            return (String) analogInputCluster.getAttributeDescription().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public int getReliability() throws ZigBeeDeviceException {
        try {
            return (Integer) analogInputCluster.getAttributeReliability().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public long getApplicationType() throws ZigBeeDeviceException {
        try {
            return (Long) analogInputCluster.getAttributeApplicationType().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public boolean getOutOfService() throws ZigBeeDeviceException {
        try {
            return (Boolean) analogInputCluster.getAttributeOutOfService().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public Float getPresentValue() throws ZigBeeDeviceException {
        try {
            return (Float) analogInputCluster.getAttributePresentValue().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public int getStatusFlags() throws ZigBeeDeviceException {
        try {
            return (Integer) analogInputCluster.getAttributeStatusFlags().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public Float getMaxPresentValue() throws ZigBeeDeviceException {
        try {
            return (Float) analogInputCluster.getAttributeMaxPresentValue().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public Float getMinPresentValue() throws ZigBeeDeviceException {
        try {
            return (Float) analogInputCluster.getAttributeMinPresentValue().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public float getResolution() throws ZigBeeDeviceException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getEngineeringUnits() throws ZigBeeDeviceException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Float getValueX() throws ZigBeeDeviceException {
        try {
            return (Float) analogInputCluster.getAttributeValueX().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public Float getValueY() throws ZigBeeDeviceException {
        try {
            return (Float) analogInputCluster.getAttributeValueY().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public Float getValueZ() throws ZigBeeDeviceException {
        try {
            return (Float) analogInputCluster.getAttributeValueZ().getValue();
        } catch (ZigBeeClusterException e) {
            throw new ZigBeeDeviceException(e);
        }
    }

    @Override
    public boolean subscribeX(PresentValueListener listener) {
        // TODO Auto-generated method stub
        return eventBridgeX.subscribe(listener);
    }

    @Override
    public boolean unsubscribeX(PresentValueListener listener) {
        // TODO Auto-generated method stub
        return eventBridgeX.unsubscribe(listener);
    }

    @Override
    public boolean subscribeY(PresentValueListener listener) {
        // TODO Auto-generated method stub
        return eventBridgeY.subscribe(listener);
    }

    @Override
    public boolean unsubscribeY(PresentValueListener listener) {
        // TODO Auto-generated method stub
        return eventBridgeY.unsubscribe(listener);
    }

    @Override
    public boolean subscribeZ(PresentValueListener listener) {
        // TODO Auto-generated method stub
        return eventBridgeZ.subscribe(listener);
    }

    @Override
    public boolean unsubscribeZ(PresentValueListener listener) {
        // TODO Auto-generated method stub
        return eventBridgeZ.unsubscribe(listener);
    }

}
