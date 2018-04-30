package org.openhab.binding.zigbee;

import com.zsmartsystems.zigbee.ZigBeeNode;

/**
 * Defines a callback used to advise the ZigBee discovery service of a new node
 *
 * @author Chris Jackson
 *
 */
public interface ZigBeeNetworkDiscoveryListener {
    /**
     * Called when a new {@link ZigBeeNode} is discovered on the network
     *
     * @param node the new {@link ZigBeeNode}
     */
    void nodeDiscovered(ZigBeeNode node);
}
