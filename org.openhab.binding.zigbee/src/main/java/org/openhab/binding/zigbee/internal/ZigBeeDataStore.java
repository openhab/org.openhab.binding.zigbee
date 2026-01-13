/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.core.OpenHAB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.database.ZclAttributeDao;
import com.zsmartsystems.zigbee.database.ZclClusterDao;
import com.zsmartsystems.zigbee.database.ZigBeeEndpointDao;
import com.zsmartsystems.zigbee.database.ZigBeeNetworkBackupDao;
import com.zsmartsystems.zigbee.database.ZigBeeNetworkDataStore;
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao;
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
public class ZigBeeDataStore implements ZigBeeNetworkDataStore {
    /**
     * The logger.
     */
    private final Logger logger = LoggerFactory.getLogger(ZigBeeDataStore.class);

    private final static String CHARSET = "UTF-8";

    private final String backupFilePath;
    private final String networkStateFilePath;

    public ZigBeeDataStore(String networkId) {
        networkStateFilePath = OpenHAB.getUserDataFolder() + File.separator + ZigBeeBindingConstants.BINDING_ID
                + File.separator + networkId + File.separator;

        File folder;

        folder = new File(networkStateFilePath);
        // Create the path for serialization.
        if (!folder.exists()) {
            logger.debug("Creating ZigBee persistence folder {}", networkStateFilePath);
            if (!folder.mkdirs()) {
                logger.error("Error while creating ZigBee persistence folder {}", networkStateFilePath);
            }
        }

        backupFilePath = OpenHAB.getUserDataFolder() + File.separator + ZigBeeBindingConstants.BINDING_ID
                + File.separator + "backup" + File.separator;

        folder = new File(backupFilePath);
        // Create the path for serialization.
        if (!folder.exists()) {
            logger.debug("Creating ZigBee backup folder {}", backupFilePath);
            if (!folder.mkdirs()) {
                logger.error("Error while creating ZigBee backup folder {}", networkStateFilePath);
            }
        }
    }

    private XStream openStream() {
        XStream stream = new XStream(new StaxDriver());
        stream.allowTypesByWildcard(new String[] { ZigBeeNode.class.getPackageName() + ".**" });
        stream.setClassLoader(this.getClass().getClassLoader());

        stream.alias("ZigBeeNode", ZigBeeNodeDao.class);
        stream.alias("ZigBeeEndpoint", ZigBeeEndpointDao.class);
        stream.alias("ZclCluster", ZclClusterDao.class);
        stream.alias("ZclAttribute", ZclAttributeDao.class);
        stream.alias("MacCapabilitiesType", MacCapabilitiesType.class);
        stream.alias("ServerCapabilitiesType", ServerCapabilitiesType.class);
        stream.alias("PowerSourceType", PowerSourceType.class);
        stream.alias("FrequencyBandType", FrequencyBandType.class);
        stream.alias("BindingTable", BindingTable.class);
        stream.alias("IeeeAddress", IeeeAddress.class);
        stream.alias("ZigBeeNetworkBackupDao", ZigBeeNetworkBackupDao.class);
        stream.registerConverter(new IeeeAddressConverter());
        return stream;
    }

    private File getFile(UUID uuid) {
        return new File(backupFilePath + uuid + ".xml");
    }

    private File getFile(IeeeAddress address) {
        return new File(networkStateFilePath + address + ".xml");
    }

    @Override
    public Set<IeeeAddress> readNetworkNodes() {
        Set<IeeeAddress> nodes = new HashSet<>();
        File dir = new File(networkStateFilePath);
        File[] files = dir.listFiles();

        if (files == null) {
            return nodes;
        }

        for (File file : files) {
            if (!file.getName().toLowerCase().endsWith(".xml")) {
                continue;
            }

            try {
                IeeeAddress address = new IeeeAddress(file.getName().substring(0, 16));
                nodes.add(address);
            } catch (IllegalArgumentException e) {
                logger.error("Error parsing database filename: {}", file.getName());
            }
        }

        return nodes;
    }

    @Override
    public ZigBeeNodeDao readNode(IeeeAddress address) {
        XStream stream = openStream();
        File file = getFile(address);

        ZigBeeNodeDao node = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            node = (ZigBeeNodeDao) stream.fromXML(reader);
            reader.close();
            logger.debug("{}: ZigBee reading network state complete.", address);
        } catch (Exception e) {
            logger.error("{}: Error reading network state: ", address, e);
        }

        return node;
    }

    @Override
    public void writeNode(ZigBeeNodeDao node) {
        XStream stream = openStream();
        File file = getFile(node.getIeeeAddress());

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            stream.marshal(node, new PrettyPrintWriter(writer));
            writer.close();
            logger.debug("{}: ZigBee saving network state complete.", node.getIeeeAddress());
        } catch (Exception e) {
            logger.error("{}: Error writing network state: ", node.getIeeeAddress(), e);
        }
    }

    @Override
    public void removeNode(IeeeAddress address) {
        File file = getFile(address);
        if (file.delete()) {
            logger.debug("{}: ZigBee removing network state complete", address);
        } else {
            logger.error("{}: Error removing network state", address);
        }
    }

    /**
     * Deletes the network state file
     */
    public synchronized void delete() {
        try {
            logger.debug("Deleting ZigBee network state");
            Files.walk(Paths.get(networkStateFilePath)).sorted(Comparator.reverseOrder()).map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            logger.error("Error deleting ZigBee network state {} ", networkStateFilePath, e);
        }
    }

    @Override
    public boolean writeBackup(ZigBeeNetworkBackupDao backup) {
        XStream stream = openStream();
        File file = getFile(backup.getUuid());

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), CHARSET))) {
            stream.marshal(backup, new PrettyPrintWriter(writer));
            writer.close();
        } catch (Exception e) {
            logger.error("{}: Error writing network backup: ", backup.getUuid(), e);
            return false;
        }

        return true;
    }

    @Override
    public ZigBeeNetworkBackupDao readBackup(UUID uuid) {
        XStream stream = openStream();
        File file = getFile(uuid);

        ZigBeeNetworkBackupDao backup = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), CHARSET))) {
            backup = (ZigBeeNetworkBackupDao) stream.fromXML(reader);
            reader.close();
        } catch (Exception e) {
            logger.error("{}: Error reading network backup: ", uuid, e);
        }

        return backup;
    }

    @Override
    public Set<ZigBeeNetworkBackupDao> listBackups() {
        Set<ZigBeeNetworkBackupDao> backups = new HashSet<>();
        File dir = new File(backupFilePath);
        File[] files = dir.listFiles();

        if (files == null) {
            return backups;
        }

        for (File file : files) {
            if (!file.getName().toLowerCase().endsWith(".xml")) {
                continue;
            }

            try {
                String filename = file.getName();
                UUID uuid = UUID.fromString(filename.substring(0, filename.length() - 4));
                ZigBeeNetworkBackupDao backup = readBackup(uuid);
                for (ZigBeeNodeDao node : backup.getNodes()) {
                    node.setEndpoints(null);
                    node.setBindingTable(null);
                }
                backups.add(backup);
            } catch (IllegalArgumentException e) {
                logger.error("Error parsing database filename: {}", file.getName());
            }
        }

        return backups;
    }
}
