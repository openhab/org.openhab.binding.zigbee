/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.smarthome.config.core.ConfigConstants;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeNetworkStateSerializer;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.dao.ZclClusterDao;
import com.zsmartsystems.zigbee.dao.ZigBeeEndpointDao;
import com.zsmartsystems.zigbee.dao.ZigBeeNodeDao;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zdo.field.BindingTable;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor.FrequencyBandType;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor.MacCapabilitiesType;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor.ServerCapabilitiesType;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor.PowerSourceType;

/**
 * Serializes and deserializes the ZigBee network state.
 *
 * @author Chris Jackson
 */
public class ZigBeeNetworkStateSerializerImpl implements ZigBeeNetworkStateSerializer {
    /**
     * The logger.
     */
    private final Logger logger = LoggerFactory.getLogger(ZigBeeNetworkStateSerializerImpl.class);

    /**
     * The network state filename.
     */
    private final String networkStateFileName = "zigbee-network--";

    /**
     * The networkId - used to allow multiple coordinators
     */
    private final String networkId;

    /**
     * The network state filename.
     */
    private final String networkStateFilePath;

    public ZigBeeNetworkStateSerializerImpl(String networkId) {
        this.networkId = networkId;
        networkStateFilePath = ConfigConstants.getUserDataFolder() + "/" + ZigBeeBindingConstants.BINDING_ID;
    }

    private XStream openStream() {
        final File folder = new File(networkStateFilePath);

        // Create the path for serialization.
        if (!folder.exists()) {
            logger.debug("Creating ZigBee persistence folder {}", networkStateFilePath);
            if (!folder.mkdirs()) {
                logger.error("Error while creating ZigBee persistence folder {}", networkStateFilePath);
            }
        }

        XStream stream = new XStream(new StaxDriver());
        stream.setClassLoader(ZigBeeNetworkStateSerializerImpl.class.getClassLoader());

        stream.alias("ZigBeeNode", ZigBeeNodeDao.class);
        stream.alias("ZigBeeEndpoint", ZigBeeEndpointDao.class);
        stream.alias("ZclCluster", ZclClusterDao.class);
        stream.alias("ZclAttribute", ZclAttribute.class);
        stream.alias("MacCapabilitiesType", MacCapabilitiesType.class);
        stream.alias("ServerCapabilitiesType", ServerCapabilitiesType.class);
        stream.alias("PowerSourceType", PowerSourceType.class);
        stream.alias("FrequencyBandType", FrequencyBandType.class);
        stream.alias("BindingTable", BindingTable.class);
        stream.alias("IeeeAddress", BindingTable.class);
        stream.registerConverter(new IeeeAddressConverter());
        return stream;
    }

    /**
     * Serializes the network state.
     * Note that this method needs to be synchronised as serialisation may be called from different threads.
     *
     * @param networkManager the network state
     */
    @Override
    public synchronized void serialize(final ZigBeeNetworkManager networkManager) {
        XStream stream = openStream();

        logger.debug("Saving ZigBee network state: Start.");

        final List<Object> destinations = new ArrayList<Object>();

        for (ZigBeeNode node : networkManager.getNodes()) {
            ZigBeeNodeDao nodeDao = node.getDao();
            destinations.add(nodeDao);
        }

        final File file = getNetworkStateFile();

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            stream.marshal(destinations, new PrettyPrintWriter(writer));
            writer.flush();
            writer.close();
        } catch (Exception e) {
            logger.error("Error writing network state ", e);
        }

        logger.debug("Saving ZigBee network state: Done.");
    }

    /**
     * Deserializes the network state.
     *
     * @param networkState the network state
     */
    @Override
    public void deserialize(final ZigBeeNetworkManager networkState) {
        logger.debug("Loading ZigBee network state: Start.");

        final File file = getNetworkStateFile();
        boolean networkStateExists = file.exists();
        if (!networkStateExists) {
            logger.debug("Loading ZigBee network state: File does not exist");
            return;
        }

        try {
            XStream stream = openStream();
            BufferedReader reader;
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            final List<Object> objects = (List<Object>) stream.fromXML(reader);
            for (final Object object : objects) {
                if (object instanceof ZigBeeNodeDao) {
                    ZigBeeNodeDao nodeDao = (ZigBeeNodeDao) object;
                    if (nodeDao.getNetworkAddress() == 0) {
                        ZigBeeNode node = new ZigBeeNode(networkState, new IeeeAddress(nodeDao.getIeeeAddress()));
                        node.setDao(nodeDao);
                        networkState.addNode(node);
                    }
                }
            }
            for (final Object object : objects) {
                if (object instanceof ZigBeeNodeDao) {
                    ZigBeeNodeDao nodeDao = (ZigBeeNodeDao) object;
                    if (nodeDao.getNetworkAddress() != 0) {
                        ZigBeeNode node = new ZigBeeNode(networkState, new IeeeAddress(nodeDao.getIeeeAddress()));
                        node.setDao(nodeDao);
                        networkState.addNode(node);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error loading ZigBee state ", e);
        }

        logger.debug("Loading ZigBee network state: Done.");
    }

    /**
     * Deletes the network state file
     */
    public synchronized void delete() {
        try {
            logger.debug("Deleting ZigBee network state: Start.");
            Files.deleteIfExists(getNetworkStateFile().toPath());
            logger.debug("Deleting ZigBee network state: Done.");
        } catch (IOException e) {
            logger.error("Error deleting ZigBee state ", e);
        }
    }

    private File getNetworkStateFile() {
        return new File(networkStateFilePath + "/" + networkStateFileName + networkId + ".xml");
    }

}
