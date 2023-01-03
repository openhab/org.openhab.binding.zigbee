/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
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
    private static final String INDEX_JSON = "index.json";
    private static final String PATH_TO_FIRMWARE = "firmware";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ExecutorService executor = Executors.newCachedThreadPool();

    private final Gson gson = new Gson();
    private HttpClient httpClient;

    private String repositoryAddress;

    private final List<DirectoryFileEntry> directory = new ArrayList<>();

    public GithubLibraryReader(String folder) {
    }

    public List<DirectoryFileEntry> getDirectory() {
        synchronized (directory) {
            return directory;
        }
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

        logger.debug("ZigBee Firmware Provider communicator created for {}", this.repositoryAddress);

        File folder = new File(OpenHAB.getUserDataFolder() + File.separator + ZigBeeBindingConstants.BINDING_ID
                + File.separator + PATH_TO_FIRMWARE);

        if (!folder.exists()) {
            logger.debug("ZigBee Firmware Provider creating firmware folder {}", folder);
            if (!folder.mkdirs()) {
                logger.error("ZigBee Firmware Provider error creating firmware folder {}", folder);
            }
        }

        // Check if the index is available locally and load it
        loadLocalDirectory();

        // We're done!
        return true;
    }

    public void loadLocalDirectory() {

        // processDirectory(newDirectory);
    }

    public void updateRemoteDirectory() {
        logger.debug("ZigBee Firmware Provider: Scheduling update from remote");

        Runnable commandHandler = new Runnable() {
            @Override
            public void run() {
                logger.debug("ZigBee Firmware Provider: Starting update from remote");
                List<DirectoryFileEntry> newDirectory = getIndex();

                processDirectory(newDirectory);

                createLocal(directory.get(0));
            }
        };
        scheduler.execute(commandHandler);
    }

    private void processDirectory(List<DirectoryFileEntry> newDirectory) {
        for (DirectoryFileEntry entry : newDirectory) {
            File localFile = getLocalFile(entry);
            if (localFile.exists()) {
                logger.debug("ZigBee Firmware Provider found local file '{}'", localFile);

                // Check hash and delete local file if invalid
                InputStream stream = getInputStream(entry);
                if (stream == null) {
                    logger.debug("ZigBee Firmware Provider local file '{}' failed hash check and is deleted",
                            localFile);
                    localFile.delete();
                } else {
                    try {
                        byte[] data = stream.readAllBytes();
                        stream.close();
                        createMd5Hash(data, entry);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        logger.debug("ZigBee Firmware Provider local file '{}' failed hash check and is deleted: {}",
                                localFile, e.getLocalizedMessage());
                        localFile.delete();
                    }
                }
            }
        }

        if (newDirectory == null) {
            logger.debug("ZigBee Firmware Provider directory update from GitHub failed!");
            return;
        }

        synchronized (directory) {
            directory.clear();
            directory.addAll(newDirectory);
        }
    }

    public boolean isAvailableLocally(DirectoryFileEntry entry) {
        return entry.getMd5() != null;
    }

    public boolean createLocal(DirectoryFileEntry entry) {
        // Download from remote
        String url = entry.getUrl();

        logger.debug("ZigBee Firmware Provider: Requesting GitHub request: {}", url);
        ContentResponse response;

        try {
            response = httpClient.newRequest(url).method(GET).timeout(HTTP_TIMEOUT, TimeUnit.SECONDS).send();
            if (response.getStatus() != HttpURLConnection.HTTP_OK) {
                logger.warn("ZigBee Firmware Provider return status other than HTTP_OK : {}", response.getStatus());
                return false;
            }

        } catch (TimeoutException | ExecutionException | NullPointerException e) {
            logger.warn("ZigBee Firmware Provider could not connect to server with exception: ", e);
            return false;
        } catch (InterruptedException e) {
            logger.warn("ZigBee Firmware Provider connect to server interrupted: ", e);
            Thread.currentThread().interrupt();
            return false;
        }

        byte[] data = response.getContent();

        // Check the hash
        if (checkHash(data, entry) == false) {

        }

        // Create an MD5 hash and store it in the dir entry
        try {
            createMd5Hash(data, entry);
        } catch (NoSuchAlgorithmException e1) {
            logger.error("System does not support MD5");

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
            logger.error("Can't find file {}", local.getName());
        } catch (IOException e) {
            logger.error("IO Exception writing file {}", local.getName(), e);
        }

        return true;
    }

    public InputStream getInputStream(DirectoryFileEntry entry) {
        File local = getLocalFile(entry);

        InputStream inputStream;
        byte[] data;
        try {
            inputStream = new FileInputStream(local);
            data = inputStream.readAllBytes();

            // Check the hash
            if (!checkHash(data, entry)) {
                inputStream.close();
                return null;
            }
            return inputStream;
        } catch (IOException e) {
            logger.error("IO Exception reading file {}", local.getName(), e);
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
                logger.warn("ZigBee Firmware Provider server return status other than HTTP_OK : {}",
                        response.getStatus());
                return null;
            }
        } catch (TimeoutException | ExecutionException | NullPointerException e) {
            logger.warn("ZigBee Firmware Provider could not connect to server with exception: ", e);
            return null;
        } catch (InterruptedException e) {
            logger.warn("ZigBee Firmware Provider connection to server interrupted: ", e);
            Thread.currentThread().interrupt();
            return null;
        }

        String responseString = response.getContentAsString();
        logger.debug("GitHub response: {}", responseString);

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
                logger.warn("ZigBee Firmware Provider SHA512 hash check on file {} failed", entry.getUrl());
                return false;
            }

            return true;
        } catch (NoSuchAlgorithmException e) {
            logger.warn("ZigBee Firmware Provider error checking hash on file {}: ", entry.getUrl(), e);
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
            builder.append(String.format("%02X", val));
        }

        return builder.toString();
    }

}
