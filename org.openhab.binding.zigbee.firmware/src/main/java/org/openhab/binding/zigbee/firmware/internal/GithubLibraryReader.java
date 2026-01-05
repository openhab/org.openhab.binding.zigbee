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
package org.openhab.binding.zigbee.firmware.internal;

import static org.eclipse.jetty.http.HttpMethod.GET;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeFirmwareVersion;
import org.openhab.core.OpenHAB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Manages the directory - reading the directory listing as a JSON file from GitHub
 *
 * @author Chris Jackson
 *
 */
public class GithubLibraryReader {
    private final Logger logger = LoggerFactory.getLogger(GithubLibraryReader.class);

    private static final int HTTP_TIMEOUT = 5;
    private static final int QUEUE_SIZE = 15;
    private static final int UPDATE_CHECK_PERIOD = 40000; // Approximately twice per day - unsynchronisedd
    private static final String INDEX_JSON = "index.json";
    private static final String PATH_TO_FIRMWARE = "firmware";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ExecutorService executor = Executors.newCachedThreadPool();

    private BlockingQueue<@Nullable DirectoryEntry> commandQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private @Nullable CommandProcessThread commandThread;
    private boolean closeHandler = false;
    private @Nullable ScheduledFuture<?> updateJob = null;

    private final Gson gson = new Gson();
    private HttpClient httpClient;

    private String repositoryAddress;

    private final List<DirectoryFileEntry> directory = new ArrayList<>();

    public GithubLibraryReader(String folder) {
    }

    public void close() {
        closeHandler = true;
        try {
            commandThread.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
        }
        stopUpdateJob();
    }

    public boolean create(String repositoryAddress) throws Exception {
        logger.debug("ZigBee Firmware Provider: Creating directory at {}", repositoryAddress);
        String localAddress;
        if (repositoryAddress.endsWith("/")) {
            localAddress = repositoryAddress.substring(0, repositoryAddress.length() - 1);
        } else {
            localAddress = repositoryAddress;
        }
        if (localAddress.contains("//")) {
            localAddress = localAddress.substring(localAddress.indexOf("//") + 2);
        }
        this.repositoryAddress = localAddress + "/";

        Client sslContext = new SslContextFactory.Client();
        this.httpClient = new HttpClient(sslContext);
        this.httpClient.getSslContextFactory().setTrustAll(true);
        this.httpClient.getSslContextFactory().setValidateCerts(false);
        this.httpClient.getSslContextFactory().setValidatePeerCerts(false);
        this.httpClient.getSslContextFactory().setEndpointIdentificationAlgorithm(null);
        this.httpClient.setExecutor(executor);

        try {
            this.httpClient.start();
        } catch (Exception e) {
            logger.debug("ZigBee Firmware Provider: Cannot start HttpClient for GitHub connection!");
            return false;
        }

        logger.debug("ZigBee Firmware Provider: communicator created for {}", this.repositoryAddress);

        File folder = new File(OpenHAB.getUserDataFolder() + File.separator + ZigBeeBindingConstants.BINDING_ID
                + File.separator + PATH_TO_FIRMWARE);

        if (!folder.exists()) {
            logger.debug("ZigBee Firmware Provider: creating firmware folder {}", folder);
            if (!folder.mkdirs()) {
                logger.error("ZigBee Firmware Provider: error creating firmware folder {}", folder);
            }
        }

        commandThread = new CommandProcessThread();
        commandThread.start();

        startUpdateJob();

        // We're done!
        return true;
    }

    private void processDirectory(List<DirectoryFileEntry> newDirectory) {
        logger.debug("ZigBee Firmware Provider: Processing directory with {} entries", newDirectory.size());
        for (DirectoryFileEntry entry : newDirectory) {
            File localFile = getLocalFile(entry);
            if (localFile.exists()) {
                logger.debug("ZigBee Firmware Provider: Found local file '{}'", localFile);

                // Check hash and delete local file if invalid
                InputStream stream = getInputStream(entry);
                if (stream == null) {
                    logger.debug("ZigBee Firmware Provider: Local file '{}' failed hash check and is deleted",
                            localFile);
                    localFile.delete();
                } else {
                    try {
                        byte[] data = stream.readAllBytes();
                        stream.close();
                        createMd5Hash(data, entry);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        logger.debug("ZigBee Firmware Provider: Local file '{}' failed hash check and is deleted: {}",
                                localFile, e.getLocalizedMessage());
                        localFile.delete();
                    }
                }
            }
        }

        synchronized (directory) {
            directory.clear();
            directory.addAll(newDirectory);
            logger.debug("ZigBee Firmware Provider: Directory update completed - {} entries", newDirectory.size());
        }
    }

    private boolean createLocal(DirectoryFileEntry entry) {
        // Download from remote
        String url = entry.getUrl();

        logger.debug("ZigBee Firmware Provider: Requesting GitHub file: {}", url);
        ContentResponse response;

        try {
            response = httpClient.newRequest(url).method(GET).timeout(HTTP_TIMEOUT, TimeUnit.SECONDS).send();
            if (response.getStatus() != HttpURLConnection.HTTP_OK) {
                logger.warn("ZigBee Firmware Provider: Return status other than HTTP_OK : {}", response.getStatus());
                return false;
            }

        } catch (TimeoutException | ExecutionException | NullPointerException e) {
            logger.warn("ZigBee Firmware Provider: could not connect to server with exception: ", e);
            return false;
        } catch (InterruptedException e) {
            logger.warn("ZigBee Firmware Provider: connect to server interrupted: ", e);
            Thread.currentThread().interrupt();
            return false;
        }

        byte[] data = response.getContent();
        logger.debug("ZigBee Firmware Provider: GitHub downloaded {} bytes", data.length);

        // Check the hash
        if (checkHash(data, entry) == false) {

        }

        // Create an MD5 hash and store it in the dir entry
        try {
            createMd5Hash(data, entry);
        } catch (NoSuchAlgorithmException e1) {
            logger.error("ZigBee Firmware Provider: System does not support MD5");

            return false;
        }

        // Save the file locally
        File local = getLocalFile(entry);
        local.getParentFile().mkdirs();

        try {
            OutputStream outputStream = new FileOutputStream(local);
            outputStream.write(data, 0, data.length);
            outputStream.close();
        } catch (FileNotFoundException e) {
            logger.error("ZigBee Firmware Provider: Can't find file {}", local.getName());
        } catch (IOException e) {
            logger.error("ZigBee Firmware Provider: IO Exception writing file {}", local.getName(), e);
        }

        logger.debug("ZigBee Firmware Provider: GitHub file downloaded {}", url);
        return true;
    }

    private void purgeOldFiles() {

    }

    public InputStream getInputStream(DirectoryFileEntry entry) {
        File local = getLocalFile(entry);

        InputStream inputStream;
        byte[] data;
        try {
            inputStream = new FileInputStream(local);
            data = inputStream.readAllBytes();
            inputStream.close();

            // Check the hash
            if (!checkHash(data, entry)) {
                return null;
            }

            return new FileInputStream(local);
        } catch (IOException e) {
            logger.error("ZigBee Firmware Provider: IO Exception reading file {}", local.getName(), e);
            return null;
        }
    }

    private synchronized List<DirectoryFileEntry> getIndex() {
        String url = "https://" + repositoryAddress + INDEX_JSON;

        logger.debug("ZigBee Firmware Provider: Performing GitHub request: {}", url);
        ContentResponse response;

        try {
            response = httpClient.newRequest(url).method(GET).timeout(HTTP_TIMEOUT, TimeUnit.SECONDS).send();
            if (response.getStatus() != HttpURLConnection.HTTP_OK) {
                logger.warn("ZigBee Firmware Provider: Server return status other than HTTP_OK : {}",
                        response.getStatus());
                return null;
            }
        } catch (TimeoutException | ExecutionException | NullPointerException e) {
            logger.warn("ZigBee Firmware Provider: Could not connect to server with exception: ", e);
            return null;
        } catch (InterruptedException e) {
            logger.warn("ZigBee Firmware Provider: Connection to server interrupted: ", e);
            Thread.currentThread().interrupt();
            return null;
        }

        String responseString = response.getContentAsString();
        logger.trace("GitHub response: {}", responseString);

        Type listType = new TypeToken<ArrayList<DirectoryFileEntry>>() {
        }.getType();

        return gson.fromJson(responseString, listType);
    }

    private boolean checkHash(byte[] data, DirectoryFileEntry entry) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] messageDigest = md.digest(data);

            String hash = hashToString(messageDigest);
            if (!entry.getSha512().equalsIgnoreCase(hash)) {
                logger.warn("ZigBee Firmware Provider: SHA512 hash check on file {} failed", entry.getUrl());
                return false;
            }

            return true;
        } catch (NoSuchAlgorithmException e) {
            logger.warn("ZigBee Firmware Provider: Error checking hash on file {}: ", entry.getUrl(), e);
        }

        return false;
    }

    private void createMd5Hash(byte[] data, DirectoryFileEntry entry) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] messageDigest = md.digest(data);

        entry.setMd5(hashToString(messageDigest));
    }

    public File getLocalFile(DirectoryFileEntry entry) {
        String path;

        int start = entry.getUrl().indexOf("://");
        path = entry.getUrl().substring(start + 3);

        File file = new File(OpenHAB.getUserDataFolder() + File.separator + ZigBeeBindingConstants.BINDING_ID
                + File.separator + PATH_TO_FIRMWARE + File.separator + path);

        return file;
    }

    private String hashToString(byte[] hash) {
        StringBuilder builder = new StringBuilder();

        for (byte val : hash) {
            builder.append(String.format("%02x", val));
        }

        return builder.toString();
    }

    private synchronized void downloadFile(DirectoryFileEntry entry) {
        logger.debug("ZigBee Firmware Provider: Scheduling file download [{}]", entry.getUrl());

        if (entry.getMd5() != null) {
            logger.debug("ZigBee Firmware Provider: File [{}] already exists", entry.getUrl());
            return;
        }
        commandQueue.add(entry);
    }

    private synchronized void updateDirectory() {
        logger.debug("ZigBee Firmware Provider: Scheduling directory update");

        commandQueue.add(new DirectoryFolderEntry(repositoryAddress));
    }

    private void startUpdateJob() {
        stopUpdateJob();
        logger.debug("ZigBee Firmware Provider: Starting Update Job");
        this.updateJob = scheduler.scheduleWithFixedDelay(this::updateDirectory, 10, UPDATE_CHECK_PERIOD,
                TimeUnit.SECONDS);
    }

    private void stopUpdateJob() {
        final ScheduledFuture<?> updateJob = this.updateJob;
        if (updateJob != null && !updateJob.isDone()) {
            logger.debug("ZigBee Firmware Provider: Stopping Update Job");
            updateJob.cancel(false);
        }

        this.updateJob = null;
    }

    private class CommandProcessThread extends Thread {
        CommandProcessThread() {
            super("CommandProcessThread");
        }

        @Override
        public void run() {
            DirectoryEntry command;

            while (!interrupted()) {
                if (closeHandler) {
                    break;
                }
                try {
                    command = commandQueue.take();
                    logger.debug("ZigBee Firmware Provider: Took command from queue. Queue length={}, Command={}",
                            commandQueue.size(), command);

                    if (command instanceof DirectoryFolderEntry) {
                        logger.debug("ZigBee Firmware Provider: Starting update from remote");
                        List<DirectoryFileEntry> newDirectory = getIndex();
                        processDirectory(newDirectory);
                        purgeOldFiles();
                    }

                    if (command instanceof DirectoryFileEntry) {
                        DirectoryFileEntry fileEntry = (DirectoryFileEntry) command;
                        logger.debug("ZigBee Firmware Provider: Requesting remote file from {}", fileEntry.getUrl());
                        createLocal(fileEntry);
                    }
                } catch (final InterruptedException e) {
                    logger.debug("ZigBee Firmware Provider: Queue handler InterruptedException");
                    break;
                } catch (final Exception e) {
                    logger.error("ZigBee Firmware Provider: Queue handler exception", e);
                }
            }
        }
    }

    public Set<DirectoryFileEntry> getDirectorEntries(ZigBeeFirmwareVersion requestedVersion) {
        final Set<DirectoryFileEntry> firmwareSet = new HashSet<>();
        synchronized (directory) {
            for (DirectoryFileEntry firmware : directory) {
                if (firmware.getManufacturerCode().equals(requestedVersion.getManufacturerCode())
                        && firmware.getImageType().equals(requestedVersion.getFileType())) {
                    firmwareSet.add(firmware);
                }
            }
        }

        for (DirectoryFileEntry firmware : firmwareSet) {
            if (firmware.getMd5() != null) {
                downloadFile(firmware);
            }
        }
        return firmwareSet;
    }

    public DirectoryFileEntry getDirectoryEntry(ZigBeeFirmwareVersion requestedVersion, int specificVersion) {
        synchronized (directory) {
            for (DirectoryFileEntry firmware : directory) {
                if (firmware.getManufacturerCode().equals(requestedVersion.getManufacturerCode())
                        && firmware.getImageType().equals(requestedVersion.getFileType())
                        && firmware.getVersion().equals(specificVersion)) {
                    logger.debug("ZigBee Firmware Provider: Found firmware version {}", specificVersion);
                    return firmware;
                }
            }
        }

        logger.debug("ZigBee Firmware Provider: Unable to find firmware version {}", specificVersion);
        return null;
    }
}
