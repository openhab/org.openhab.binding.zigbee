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
package org.openhab.binding.zigbee.firmware;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.zigbee.firmware.internal.DirectoryFileEntry;
import org.openhab.binding.zigbee.firmware.internal.GithubLibraryReader;
import org.openhab.binding.zigbee.handler.ZigBeeFirmwareVersion;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.core.OpenHAB;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.firmware.Firmware;
import org.openhab.core.thing.binding.firmware.FirmwareBuilder;
import org.openhab.core.thing.firmware.FirmwareProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The firmware provider using a github based repository
 *
 * @author Chris Jackson
 */
@Component(service = FirmwareProvider.class, immediate = true, configurationPid = "org.openhab.binding.zigbee.firmware")
public class ZigBeeFirmwareProvider implements FirmwareProvider {
    private Logger logger = LoggerFactory.getLogger(ZigBeeFirmwareProvider.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private GithubLibraryReader directoryReader;

    @Activate
    protected void activate() {
        logger.debug("ZigBee Firmware Provider: Activated");
        String folder = OpenHAB.getUserDataFolder() + File.separator + "firmware" + File.separator;
        directoryReader = new GithubLibraryReader(folder);
        try {
            directoryReader.create("https://raw.githubusercontent.com/Koenkk/zigbee-OTA/master");
            directoryReader.updateRemoteDirectory();
        } catch (Exception e) {
            logger.error("Exception activating ZigBee firmware provider ", e);
        }
    }

    @Deactivate
    protected void deactivate() {
        logger.debug("ZigBee Firmware Provider: Deactivated");
    }

    @Override
    public @Nullable Firmware getFirmware(@NonNull Thing thing, @NonNull String version) {
        return getFirmware(thing, version, null);
    }

    @Override
    public @Nullable Firmware getFirmware(@NonNull Thing thing, @NonNull String version, @Nullable Locale locale) {
        ZigBeeFirmwareVersion requestedVersion = getRequestedVersionFromThing(thing);
        if (requestedVersion == null) {
            return null;
        }

        List<DirectoryFileEntry> directory = directoryReader.getDirectory();
        for (DirectoryFileEntry firmware : directory) {
            if (firmware.getFirmwareVersion().equals(requestedVersion)) {
                return getZigBeeFirmware(thing.getThingTypeUID(), firmware);
            }
        }

        logger.debug("Unable to find firmware version {}", version);
        return null;
    }

    @Override
    public @Nullable Set<@NonNull Firmware> getFirmwares(@NonNull Thing thing) {
        return getFirmwares(thing, null);
    }

    @Override
    public @Nullable Set<@NonNull Firmware> getFirmwares(@NonNull Thing thing, @Nullable Locale locale) {
        final Set<Firmware> firmwareSet = new HashSet<>();

        ZigBeeFirmwareVersion requestedVersion = getRequestedVersionFromThing(thing);
        if (requestedVersion == null) {
            return firmwareSet;
        }

        for (DirectoryFileEntry firmware : directoryReader.getDirectory()) {
            if (firmware.getFirmwareVersion().equals(requestedVersion)) {
                firmwareSet.add(getZigBeeFirmware(thing.getThingTypeUID(), firmware));
            }
        }
        return firmwareSet;
    }

    private ZigBeeFirmwareVersion getRequestedVersionFromThing(@NonNull Thing thing) {
        // We only deal in ZigBee devices here
        if (!(thing.getHandler() instanceof ZigBeeThingHandler)) {
            return null;
        }
        ZigBeeThingHandler zigbeeHandler = (ZigBeeThingHandler) thing.getHandler();
        if (zigbeeHandler == null) {
            return null;
        }
        return zigbeeHandler.getRequestedFirmwareVersion();
    }

    private Firmware getZigBeeFirmware(@NonNull ThingTypeUID thingTypeUID, DirectoryFileEntry directoryEntry) {
        FirmwareBuilder builder = FirmwareBuilder.create(thingTypeUID, directoryEntry.getVersion().toString());

        if (!directoryEntry.getModel().isEmpty()) {
            builder.withModel(directoryEntry.getModel());
        }
        if (!directoryEntry.getVendor().isEmpty()) {
            builder.withVendor(directoryEntry.getVendor());
        }
        if (!directoryEntry.getDescription().isEmpty()) {
            builder.withDescription(directoryEntry.getDescription());
        }
        if (!directoryEntry.getMd5().isEmpty()) {
            builder.withMd5Hash(directoryEntry.getMd5());
        }
        if (!directoryEntry.getPrerequisiteVersion().isEmpty()) {
            builder.withPrerequisiteVersion(directoryEntry.getPrerequisiteVersion());
        }

        return builder.build();
    }

}