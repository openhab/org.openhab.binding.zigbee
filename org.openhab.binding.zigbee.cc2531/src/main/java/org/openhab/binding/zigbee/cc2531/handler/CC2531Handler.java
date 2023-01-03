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
package org.openhab.binding.zigbee.cc2531.handler;

import java.util.HashSet;
import java.util.Set;

import org.openhab.binding.zigbee.cc2531.internal.CC2531Configuration;
import org.openhab.binding.zigbee.converter.ZigBeeChannelConverterFactory;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.thing.Bridge;
import org.osgi.service.component.annotations.Activate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;

/**
 * The {@link CC2531Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public class CC2531Handler extends ZigBeeCoordinatorHandler {
    private final Logger logger = LoggerFactory.getLogger(CC2531Handler.class);

    private final SerialPortManager serialPortManager;

    @Activate
    public CC2531Handler(Bridge coordinator, SerialPortManager serialPortManager,
            ZigBeeChannelConverterFactory channelFactory) {
        super(coordinator, channelFactory);
        this.serialPortManager = serialPortManager;
    }

    @Override
    protected void initializeDongle() {
        logger.debug("Initializing ZigBee CC2531 serial bridge handler.");

        CC2531Configuration config = getConfigAs(CC2531Configuration.class);
        ZigBeeTransportTransmit dongle = createDongle(config);
        TransportConfig transportConfig = createTransportConfig(config);

        startZigBee(dongle, transportConfig, DefaultSerializer.class, DefaultDeserializer.class);
    }

    private ZigBeeTransportTransmit createDongle(CC2531Configuration config) {
        ZigBeePort serialPort = new org.openhab.binding.zigbee.serial.ZigBeeSerialPort(serialPortManager,
                config.zigbee_port, config.zigbee_baud, FlowControl.FLOWCONTROL_OUT_RTSCTS);

        ZigBeeTransportTransmit dongle = new ZigBeeDongleTiCc2531(serialPort);

        logger.debug("ZigBee CC2531 Coordinator opening Port:'{}' PAN:{}, EPAN:{}, Channel:{}", config.zigbee_port,
                Integer.toHexString(panId), extendedPanId, Integer.toString(channelId));

        return dongle;
    }

    private TransportConfig createTransportConfig(CC2531Configuration config) {
        TransportConfig transportConfig = new TransportConfig();

        // The CC2531EMK dongle doesn't pass the MatchDescriptor commands to the stack, so we can't manage our services
        // directly. Instead, register any services we want to support so the CC2531EMK can handle the MatchDescriptor.
        Set<Integer> clusters = new HashSet<>();
        clusters.add(ZclIasZoneCluster.CLUSTER_ID);
        transportConfig.addOption(TransportConfigOption.SUPPORTED_OUTPUT_CLUSTERS, clusters);

        return transportConfig;
    }

}
