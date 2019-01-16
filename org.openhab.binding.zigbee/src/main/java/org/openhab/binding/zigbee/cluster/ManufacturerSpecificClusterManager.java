package org.openhab.binding.zigbee.cluster;

import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterTypeRegistry;

/**
 * This class takes care that manufacturer-specific clusters are added to the system as soon as they are provided.
 *
 * @author Henning Sudbrock - initial contribution
 */
@Component(immediate = true)
public class ManufacturerSpecificClusterManager {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ZclClusterTypeRegistry clusterTypeRegistry = ZclClusterTypeRegistry.getInstance();

    @Reference(cardinality = MULTIPLE, policy = DYNAMIC)
    protected void addManufacturerSpecificClusterProvider(ManufacturerSpecificClusterProvider provider) {
        for (ZclClusterType clusterType : provider.getProvidedClusterTypes()) {
            ZclClusterType existingCluster = clusterTypeRegistry
                    .getByManufacturerAndClusterId(clusterType.getManufacturerCode(), clusterType.getId());

            if (existingCluster != null) {
                logger.info("Do not register cluster type {}, as there is already a cluster with id {}", clusterType,
                        clusterType.getId());
            } else {
                logger.info("Registering cluster type {}", clusterType);
                clusterTypeRegistry.addClusterType(clusterType);
            }
        }
    }

    protected void removeManufacturerSpecificClusterProvider(ManufacturerSpecificClusterProvider provider) {
        for (ZclClusterType clusterType : provider.getProvidedClusterTypes()) {
            clusterTypeRegistry.removeClusterType(clusterType);
        }
    }

}
