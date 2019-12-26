/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee.ZigBeeCommandParameter;
import org.openhab.binding.zigbee.ZigBeeCommandParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The {@link ZigBeeCommandParametersImpl} class provides a stateless and a usage-tracking
 * implementations of {@link ZigBeeCommandParameters}.
 *
 * @author Thomas Weißschuh - Initial contribution
 */
@NonNullByDefault
public class ZigBeeCommandParametersImpl implements ZigBeeCommandParameters {
    private static final Logger logger = LoggerFactory.getLogger(ZigBeeCommandParametersImpl.class);

    private final Map<ZigBeeCommandParameter<?>, Object> params = new HashMap<>();

    @Override
    public <T> ZigBeeCommandParameters add(final ZigBeeCommandParameter<T> param, final T value) {
        params.put(param, value);
        return this;
    }

    @Override
    public <T> Optional<T> get(final ZigBeeCommandParameter<T> param) {
        @Nullable Object v = params.get(param);
        if (v == null) {
            return Optional.empty();
        } else if (!param.getType().isInstance(v)) {
            logger.warn("Can not retrieve param {}: object of type {} ({}) can not be casted to {}",
                    param, v.getClass(), v, param.getType()
            );
            return Optional.empty();
        } else {
            return Optional.of(param.getType().cast(v));
        }
    }

    @Override
    public Collection<ZigBeeCommandParameter<?>> setParameters() {
        return Collections.unmodifiableSet(params.keySet());
    }

    public static class UsageTracker implements ZigBeeCommandParameters {
        private final Set<ZigBeeCommandParameter<?>> usedParams = new HashSet<>();
        private final ZigBeeCommandParameters delegate;

        public UsageTracker(ZigBeeCommandParameters delegate) {
            this.delegate = delegate;
        }

        public Set<ZigBeeCommandParameter<?>> unusedParams() {
            Set<ZigBeeCommandParameter<?>> unusedParams = new HashSet<>(setParameters());
            unusedParams.removeAll(usedParams);
            return Collections.unmodifiableSet(unusedParams);
        }

        @Override
        public <T> ZigBeeCommandParameters add(ZigBeeCommandParameter<T> param, T value) {
            return delegate.add(param, value);
        }

        @Override
        public <T> Optional<T> get(ZigBeeCommandParameter<T> param) {
            usedParams.add(param);
            return delegate.get(param);
        }

        @Override
        public Collection<ZigBeeCommandParameter<?>> setParameters() {
            return delegate.setParameters();
        }
    }
}
