package org.openhab.binding.zigbee.clusters.centralite;

import static java.util.Arrays.asList;

import java.util.Collection;

import org.openhab.binding.zigbee.cluster.ManufacturerSpecificClusterProvider;
import org.osgi.service.component.annotations.Component;

import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * This class provides the following manufacturer-specific clusters used by Centralite devices:
 * <ul>
 * <li>cluster 0xFC45 - this cluster seems to work just like the relative humidity measurement cluster 0x0405, but is
 * provided under a manufacturer-specific cluster id.
 * </ul>
 *
 * @author Henning Sudbrock - initial contribution
 */
@Component(immediate = true)
public class CentraliteManufacturerSpecificClusterProvider implements ManufacturerSpecificClusterProvider {

    @Override
    public Collection<ZclClusterType> getProvidedClusterTypes() {
        return asList(createRelativeHumidityCluster());
    }

    private ZclClusterType createRelativeHumidityCluster() {
        return new ZclClusterType() {

            @Override
            public String getLabel() {
                return "Centralite Relative Humidity Measurement";
            }

            @Override
            public int getId() {
                return CentraliteRelativeHumidityMeasurementCluster.CLUSTER_ID;
            }

            @Override
            public Class<? extends ZclCluster> getClusterClass() {
                return CentraliteRelativeHumidityMeasurementCluster.class;
            }

            @Override
            public boolean isManufacturerSpecific() {
                return true;
            }

            @Override
            public Integer getManufacturerCode() {
                return CentraliteManufacturerSpecificConstants.MANUFACTURER_CODE;
            }

            @Override
            public String toString() {
                return "CENTRALITE HUMIDITY";
            }

        };
    }

}
