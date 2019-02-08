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
import java.util.Date;
import java.text.SimpleDateFormat;

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
 * @author Pedro Garcia - Atomic (mostly) file operations
 * 
 *         Serialization of the network state to a file is done in the most atomic way portability allows for:
 *         - Serialization is done to a temporary file so that the network state file, if present, is always a complete,
 *         backupable, working version
 *         - Only once the temporary file is fully writen, the old file is renamed to a backup copy (instead of deleted,
 *         to make sure the operation will work even if the file is open)
 *         - The temporary file is then renamed to the final network state file, and the backup copy is removed
 *
 *         Deserialization works as follows:
 *         - If the network state file is not present, and the temporary one is, the temporary one is renamed to the
 *         target network state file. We can assume it is a complete version of the file, as the old network state file
 *         is only moved out once the new temporary one is fully written
 *         - The network state file is then normally read
 *
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

    private final Object concurrentWriteCountSync = new Object();
    private int concurrentWriteCount = 0;

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
    private synchronized void doSerialize(final ZigBeeNetworkManager networkManager) {
        XStream stream = openStream();

        logger.debug("Saving ZigBee network state: Start.");

        final List<Object> destinations = new ArrayList<Object>();

        for (ZigBeeNode node : networkManager.getNodes()) {
            ZigBeeNodeDao nodeDao = node.getDao();
            destinations.add(nodeDao);
        }

        final File file = getNetworkStateFile();
        final File temp = new File(file.getAbsolutePath() + ".new");
        final File backup = new File(
                file.getAbsolutePath() + "." + new SimpleDateFormat("yyyyMMdd.HHmmss.SSS").format(new Date()));

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp), "UTF-8"));
            stream.marshal(destinations, new PrettyPrintWriter(writer));
            writer.flush();
            writer.close();

            if (file.exists() && !file.renameTo(backup)) {
                logger.warn("Error moving out old network state file (new state will not be saved)");
                temp.delete();
            } else if (!temp.renameTo(file)) {
                logger.warn("Error renaming new network state file. Recovering backup (new state will not be saved)");
                if (backup.exists() && !backup.renameTo(file)) {
                    logger.warn("Error recovering backup network state file");
                }
            }

            if (backup.exists() && !backup.delete()) {
                logger.debug("Unable to delete old network state file");
            }

        } catch (Exception e) {
            logger.error("Error writing network state ", e);
        }

        logger.debug("Saving ZigBee network state: Done.");
    }

    /**
     * Serializes the network state.
     * Synchonization is handled within the method with a latching mechanism: the first concurrent call will
     * wait till the ongoing write finishes. The rest of concurrent calls will be skipped returning
     * immediately. This guarrantees we write the file immediately after the first call, and only once more
     * for all concurrent calls that happened meanwhile
     *
     * @param networkManager the network state
     */

    @Override
    public void serialize(final ZigBeeNetworkManager networkManager) {

        boolean wait = false;

        synchronized (concurrentWriteCountSync) {
            if (concurrentWriteCount == 2) {
                logger.debug("Skipping ZigBee network state save (another thread already waiting)");
                return;
            }
            wait = concurrentWriteCount > 0;
            concurrentWriteCount++;
        }

        try {
            if (!wait) {
                logger.debug("Saving ZigBee network state");
            } else {
                logger.debug("Deferring ZigBee network state file save (another thread already writing)");
                synchronized (concurrentWriteCountSync) {
                    // Check again, as the writing thread might already have finished when we get here
                    if (concurrentWriteCount > 1)
                        concurrentWriteCountSync.wait();
                }
                logger.debug("Resuming ZigBee network state file save (previous thread finished)");
            }
        } catch (Exception e) {
            // Eat me up. The file will anyway be saved as this is still pending
            // The doSerialize method is synchronized, so it will wait if necessary
            logger.debug("Error waiting to write network state file", e);
        }

        try {
            doSerialize(networkManager);
        } catch (Exception e) {
            logger.debug("Error writing the network state file", e);
        }

        synchronized (concurrentWriteCountSync) {
            concurrentWriteCount--;
            concurrentWriteCountSync.notify();
        }
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
            final File temp = new File(file.getAbsolutePath() + ".new");
            if (!temp.exists()) {
                logger.debug("Loading ZigBee network state: File does not exist");
                return;
            }

            logger.warn("Recovering network state file from temporary copy");
            if (!temp.renameTo(file)) {
                logger.warn("Error recovering network state file");
                return;
            }
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
