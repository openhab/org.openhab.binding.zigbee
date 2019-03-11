package org.openhab.binding.zigbee.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterBuilder;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;

/**
 * Provides {@link ConfigDescriptionParameter}s which are supposed to exist in all things
 */
public class ZigBeeConfigDescriptionParameters {

    private static final String PARAM_ZIGBEE_INITIALIZE_DEVICE_LABEL = "Initialize device";

    private static List<ConfigDescriptionParameter> configDescriptionParameters = Collections
            .unmodifiableList(createConfigDescriptionParameters());

    /**
     * Provides the list of {@link ConfigDescriptionParameter}
     *
     * @return list of {@link ConfigDescriptionParameter}
     */
    public static List<ConfigDescriptionParameter> getParameters() {
        return configDescriptionParameters;
    }

    private static List<ConfigDescriptionParameter> createConfigDescriptionParameters() {
        List<ConfigDescriptionParameter> configDescriptionParameters = new ArrayList<>();

        configDescriptionParameters.add(ConfigDescriptionParameterBuilder
                .create(ZigBeeBindingConstants.CONFIGURATION_INITIALIZE_DEVICE, ConfigDescriptionParameter.Type.BOOLEAN)
                .withLabel(PARAM_ZIGBEE_INITIALIZE_DEVICE_LABEL).withDefault(Boolean.FALSE.toString())
                .withAdvanced(Boolean.TRUE).withRequired(Boolean.FALSE).build());

        return configDescriptionParameters;
    }

}
