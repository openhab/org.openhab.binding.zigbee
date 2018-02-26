package org.openhab.binding.zigbee.internal.converter.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter.Type;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterBuilder;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.ParameterOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;

/**
 * Configuration handler for the {@link ZclLevelControlCluster}
 *
 * @author Chris Jackson
 *
 */
public class ZclLevelControlConfig implements ZclClusterConfigHandler {
    private Logger logger = LoggerFactory.getLogger(ZclLevelControlConfig.class);

    private final String CONFIG_DEFAULTTRANSITIONTIME = "zigbee_levelcontrol_transitiontimedefault";
    private final String CONFIG_ONOFFTRANSITIONTIME = "zigbee_levelcontrol_transitiontimeonoff";
    private final String CONFIG_ONTRANSITIONTIME = "zigbee_levelcontrol_transitiontimeon";
    private final String CONFIG_OFFTRANSITIONTIME = "zigbee_levelcontrol_transitiontimeoff";
    private final String CONFIG_ONLEVEL = "zigbee_levelcontrol_onlevel";
    private final String CONFIG_DEFAULTMOVERATE = "zigbee_levelcontrol_defaultrate";

    private final ZclLevelControlCluster cluster;
    private int defaultTransitionTime = 10;

    public ZclLevelControlConfig(ZclLevelControlCluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public List<ConfigDescriptionParameter> getConfiguration() {
        try {
            Boolean result = cluster.discoverAttributes(false).get();
            if (!result) {
                logger.debug("{}: Unable to get supported attributes for {}.", cluster.getZigBeeAddress(),
                        cluster.getClusterName());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("{}: Error getting supported attributes for {}. ", cluster.getZigBeeAddress(),
                    cluster.getClusterName(), e);
        }

        // Build a list of configuration supported by this channel based on the attributes the cluster supports
        List<ConfigDescriptionParameter> parameters = new ArrayList<ConfigDescriptionParameter>();

        List<ParameterOption> options = new ArrayList<ParameterOption>();
        parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONOFFTRANSITIONTIME, Type.INTEGER)
                .withLabel("On/Off Transition Time")
                .withDescription("Time in 10ms intervals to transition between ON and OFF").withDefault("0")
                .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                .withLimitToOptions(false).build());

        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONOFFTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONOFFTRANSITIONTIME, Type.INTEGER)
                    .withLabel("On/Off Transition Time")
                    .withDescription("Time in 10ms intervals to transition between ON and OFF").withDefault("0")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("65535", "Use On/Off transition time"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONTRANSITIONTIME, Type.INTEGER)
                    .withLabel("On Transition Time")
                    .withDescription("Time in 10ms intervals to transition from OFF to ON").withDefault("65535")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_OFFTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("65535", "Use On/Off transition time"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_OFFTRANSITIONTIME, Type.INTEGER)
                    .withLabel("Off Transition Time")
                    .withDescription("Time in 10ms intervals to transition from ON to OFF").withDefault("65535")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONLEVEL)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("255", "Not Set"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONLEVEL, Type.INTEGER)
                    .withLabel("Off Transition Time").withDescription("Default On level").withDefault("255")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (cluster.isAttributeSupported(ZclLevelControlCluster.ATTR_DEFAULTMOVERATE)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("255", "Not Set"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_DEFAULTMOVERATE, Type.INTEGER)
                    .withLabel("Default move rate").withDescription("Move rate in steps per second").withDefault("255")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }

        return parameters;
    }

    @Override
    public void updateConfiguration(@NonNull Configuration configuration, @NonNull Configuration updatedConfiguration) {
        for (String property : configuration.getProperties().keySet()) {
            logger.debug("{}: Update configuration property {}->{} ({})", cluster.getZigBeeAddress(), property,
                    configuration.get(property), configuration.get(property).getClass().getSimpleName());
            switch (property) {
                case CONFIG_ONOFFTRANSITIONTIME:
                    BigDecimal value = (BigDecimal) configuration.get(property);
                    cluster.setOnOffTransitionTime(value.intValue());
                    Integer response = cluster.getOnOffTransitionTime(0);
                    if (response != null) {
                        updatedConfiguration.put(property, BigInteger.valueOf(response));
                    }
                    break;
                case CONFIG_DEFAULTTRANSITIONTIME:
                    defaultTransitionTime = ((BigDecimal) configuration.get(property)).intValue();
                    break;
                default:
                    logger.warn("{}: Unhandled configuration property {}", cluster.getZigBeeAddress(), property);
                    break;
            }
        }
    }

    /**
     * Gets the default transition time to be used when sending commands to the cluster
     *
     * @return the current defaultTransitionTime
     */
    public int getDefaultTransitionTime() {
        return defaultTransitionTime;
    }
}
