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
package org.openhab.binding.zigbee.slzb06.internal.discovery;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import org.openhab.binding.zigbee.slzb06.Slzb06BindingConstants;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Slzb06MDNSDiscoveryParticipant} is responsible for discovering SLZB06 interfaces. It uses the central
 * {@link org.openhab.core.config.discovery.mdns.internal.MDNSDiscoveryService}.
 *
 * @author Chris Jackson - Initial contribution
 */
@Component(configurationPid = "discovery.slzb06")
public class Slzb06MDNSDiscoveryParticipant implements MDNSDiscoveryParticipant {
    private final Logger logger = LoggerFactory.getLogger(Slzb06MDNSDiscoveryParticipant.class);
    private final String SERVICE_TYPE = "_slzb-06._tcp.local.";
    private final String APPLICATION = "slzb-06";

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Set.of(Slzb06BindingConstants.THING_TYPE_SLZB06);
    }

    @Override
    public String getServiceType() {
        logger.debug("SLZB-06: Discovery getServiceType '{}'", SERVICE_TYPE);
        return SERVICE_TYPE;
    }

    @Override
    public DiscoveryResult createResult(ServiceInfo service) {
        logger.debug("SLZB-06: Discovery createResult - application={}, service={}", service.getApplication(),
                service.getName());
        if (service.getApplication().contains(APPLICATION)) {
            ThingUID uid = getThingUID(service);

            if (uid != null) {
                final Map<String, Object> properties = new HashMap<>(2);
                String hostAddress = service.getName() + "." + service.getDomain() + ".";
                BigDecimal hostPort = new BigDecimal(service.getPort());
                properties.put(Slzb06BindingConstants.HOST, hostAddress);
                properties.put(Slzb06BindingConstants.PORT, hostPort);
                return DiscoveryResultBuilder.create(uid).withProperties(properties)
                        .withRepresentationProperty(Slzb06BindingConstants.HOST)
                        .withLabel("SLZB06-Server [" + service.getName() + "]").build();
            }
        }
        return null;
    }

    @Override
    public ThingUID getThingUID(ServiceInfo service) {
        logger.debug("SLZB-06: Discovery getThingUID - application={}, service={}", service.getApplication(),
                service.getName());
        if (service.getApplication().contains(APPLICATION)) {
            String hostAddress = service.getName() + "." + service.getDomain() + ".";
            logger.debug("mDNS discovering host {}", hostAddress);
            return new ThingUID(Slzb06BindingConstants.THING_TYPE_SLZB06, service.getName());
        }
        return null;
    }

}
