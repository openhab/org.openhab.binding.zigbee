/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal.converter.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zsmartsystems.zigbee.zcl.clusters.ZclIasWdCluster;

/**
 * Configuration handler for the IAS WD (Warning Device) cluster.
 *
 * @author Henning Sudbrock - initial contribution
 */
public class ZclWarningDeviceConfig {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String CONFIG_PREFIX = "zigbee_iaswd_";
    private static final String CONFIG_MAXDURATION = CONFIG_PREFIX + "maxDuration";

    private ZclIasWdCluster iasWdCluster;

    public void setCluster(ZclIasWdCluster iasWdCluster) {
        this.iasWdCluster = iasWdCluster;
    }

    public void updateConfiguration(@NonNull Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        for (Entry<String, Object> updatedParameter : updatedParameters.entrySet()) {
            if (updatedParameter.getKey().startsWith(CONFIG_PREFIX)) {
                if (Objects.equals(updatedParameter.getValue(), currentConfiguration.get(updatedParameter.getKey()))) {
                    logger.debug("Configuration update: Ignored {} as no change", updatedParameter.getKey());
                } else {
                    updateConfigParameter(currentConfiguration, updatedParameter);
                }
            }
        }
    }

    private void updateConfigParameter(Configuration currentConfiguration, Entry<String, Object> updatedParameter) {
        logger.debug("{}: Update IAS WD configuration property {}->{} ({})", iasWdCluster.getZigBeeAddress(),
                updatedParameter.getKey(), updatedParameter.getValue(),
                updatedParameter.getValue().getClass().getSimpleName());

        if (CONFIG_MAXDURATION.equals(updatedParameter.getKey())) {
            iasWdCluster.setMaxDuration(((BigDecimal) (updatedParameter.getValue())).intValue());
            Integer response = iasWdCluster.getMaxDuration(0);

            if (response != null) {
                currentConfiguration.put(updatedParameter.getKey(), BigInteger.valueOf(response));
            }
        } else {
            logger.warn("{}: Unhandled configuration property {}", iasWdCluster.getZigBeeAddress(),
                    updatedParameter.getKey());
        }
    }

}
