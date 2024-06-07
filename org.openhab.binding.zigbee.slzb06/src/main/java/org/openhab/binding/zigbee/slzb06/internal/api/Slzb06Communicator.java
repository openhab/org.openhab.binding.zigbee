/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.zigbee.slzb06.internal.api;

import static org.eclipse.jetty.http.HttpMethod.GET;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.openhab.binding.zigbee.slzb06.internal.api.Slzb06SensorsOuter.Slzb06Sensors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link Slzb06Communicator} handles communication with the SLZB06 configuration API.
 *
 * @author Chris Jackson - Initial contribution
 */
@NonNullByDefault
public class Slzb06Communicator {

    private static final String CMD_SENSORS = "/ha_sensors";
    private static final String CMD_INFO = "/ha_info";

    private static final int HTTP_TIMEOUT = 13;

    private final Logger logger = LoggerFactory.getLogger(Slzb06Communicator.class);
    private final HttpClient httpClient;

    private final String address;

    private final Gson gson = new Gson();
    private ExecutorService executor = Executors.newCachedThreadPool();

    public Slzb06Communicator(String address) throws Slzb06Exception {
        String localAddress;
        if (address.endsWith("/")) {
            localAddress = address.substring(0, address.length() - 1);
        } else {
            localAddress = address;
        }
        if (localAddress.contains("//")) {
            localAddress = localAddress.substring(localAddress.indexOf("//") + 2, address.length() - 1);
        }
        this.address = localAddress;

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
            throw new Slzb06Exception("Cannot start HttpClient!");
        }

        logger.debug("SLZB06 communicator created for {}", this.address);
    }

    @Override
    public void finalize() {
        try {
            httpClient.stop();
        } catch (Exception e) {
            // swallow this
        }
    }

    public Slzb06Sensors getSensors() throws Slzb06Exception {
        return ((Slzb06SensorsOuter) sendGet(CMD_SENSORS, Slzb06SensorsOuter.class)).Sensors;
    }

    public Slzb06Information getInformation() throws Slzb06Exception {
        return (Slzb06Information) sendGet(CMD_INFO, Slzb06Information.class);
    }

    private synchronized Slzb06Response sendGet(String command, Class<? extends Slzb06Response> typeRef)
            throws Slzb06Exception {
        String url = "http://" + address + command;
        logger.debug("SLZB06 request: {}", url);
        ContentResponse response;

        try {
            response = httpClient.newRequest(url).method(GET).timeout(HTTP_TIMEOUT, TimeUnit.SECONDS).send();
            if (response.getStatus() != HttpURLConnection.HTTP_OK) {
                logger.warn("SLZB06 return status other than HTTP_OK : {}", response.getStatus());
                throw new Slzb06Exception("SLZB06 return status other than HTTP_OK: " + response.getStatus());
            }
        } catch (TimeoutException | ExecutionException | NullPointerException e) {
            logger.warn("Could not connect to SLZB06 with exception: ", e);
            throw new Slzb06Exception("Could not connect to SLZB06 with exception: " + e.getMessage());
        } catch (InterruptedException e) {
            logger.warn("Connect to SLZB06 interrupted: ", e);
            Thread.currentThread().interrupt();
            throw new Slzb06Exception("Connect to SLZB06 interrupted: " + e.getMessage());
        }

        String responseString = response.getContentAsString();
        logger.debug("SLZB06 response: {}", responseString);

        return gson.fromJson(responseString, typeRef);
    }
}
