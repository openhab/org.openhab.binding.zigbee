package org.openhab.binding.zigbee.slzb06.internal.discovery;

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

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Set.of(Slzb06BindingConstants.THING_TYPE_SLZB06);
    }

    @Override
    public String getServiceType() {
        return "_slzb-06.tcp.";
    }

    @Override
    public DiscoveryResult createResult(ServiceInfo service) {
        if (service.getApplication().contains("dssweb")) {
            ThingUID uid = getThingUID(service);

            if (uid != null) {
                String hostAddress = service.getName() + "." + service.getDomain() + ".";
                Map<String, Object> properties = new HashMap<>(2);
                properties.put(Slzb06BindingConstants.HOST, hostAddress);
                return DiscoveryResultBuilder.create(uid).withProperties(properties)
                        .withRepresentationProperty(uid.getId()).withLabel("SLZB06-Server [" + service.getName() + "]")
                        .build();
            }
        }
        return null;
    }

    @Override
    public ThingUID getThingUID(ServiceInfo service) {
        if (service.getApplication().contains("slzb06")) {
            String hostAddress = service.getName() + "." + service.getDomain() + ".";
            logger.debug("mDNS discovering host {}", hostAddress);
            return new ThingUID(Slzb06BindingConstants.THING_TYPE_SLZB06, service.getName());
        }
        return null;
    }

}
