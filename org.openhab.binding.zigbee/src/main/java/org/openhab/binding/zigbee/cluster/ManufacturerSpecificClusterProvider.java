package org.openhab.binding.zigbee.cluster;

import java.util.Collection;

import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;

/**
 * Interface for providers that contribute manufacturer-specific clusters used by the binding.
 *
 * @author Henning Sudbrock - initial contribution
 */
public interface ManufacturerSpecificClusterProvider {

    /**
     * @return a collection with provided manufacturer-specific cluster types
     */
    Collection<ZclClusterType> getProvidedClusterTypes();

}
