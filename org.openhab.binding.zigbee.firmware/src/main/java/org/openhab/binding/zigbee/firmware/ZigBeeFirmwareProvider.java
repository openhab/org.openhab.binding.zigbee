/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    private GithubLibraryReader directoryReader;

    @Activate
    protected void activate() throws Exception {
        logger.debug("ZigBee Firmware Provider: Activated");

        String folder = OpenHAB.getUserDataFolder() + File.separator + "firmware" + File.separator;
        directoryReader = new GithubLibraryReader(folder);
        try {
            directoryReader.create("https://raw.githubusercontent.com/Koenkk/zigbee-OTA/master");
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
        ZigBeeFirmwareVersion requestedVersion = getThingRequestedVersion(thing);
        if (requestedVersion == null) {
            return null;
        }

        int specificVersion;
        try {
            specificVersion = Integer.parseInt(version);
        } catch (NumberFormatException e) {
            logger.info("ZigBee Firmware Provider: Requested version {} for {} is not an integer", version,
                    thing.getUID());
            return null;
        }
        logger.debug("ZigBee Firmware Provider: Getting version {} of {}", specificVersion, thing.getUID());

        DirectoryFileEntry directory = directoryReader.getDirectoryEntry(requestedVersion, specificVersion);

        if (directory == null) {
            return null;
        }

        InputStream inputStream = directoryReader.getInputStream(directory);
        if (inputStream == null) {
            return null;
        }

        return getZigBeeFirmware(thing.getThingTypeUID(), directory, inputStream);
    }

    @Override
    public @Nullable Set<@NonNull Firmware> getFirmwares(@NonNull Thing thing) {
        return getFirmwares(thing, null);
    }

    @Override
    public @Nullable Set<@NonNull Firmware> getFirmwares(@NonNull Thing thing, @Nullable Locale locale) {
        ZigBeeFirmwareVersion requestedVersion = getThingRequestedVersion(thing);
        if (requestedVersion == null) {
            return Collections.emptySet();
        }

        final Set<DirectoryFileEntry> directorySet = directoryReader.getDirectorEntries(requestedVersion);
        final Set<Firmware> firmwareSet = new HashSet<>();
        for (DirectoryFileEntry firmware : directorySet) {
            firmwareSet.add(getZigBeeFirmware(thing.getThingTypeUID(), firmware));
        }

        logger.debug("ZigBee Firmware Provider: Thing {} has {} firmwares available", thing.getUID(),
                firmwareSet.size());
        return firmwareSet;
    }

    private ZigBeeFirmwareVersion getThingRequestedVersion(@NonNull Thing thing) {
        // We only deal in ZigBee devices here
        if (!(thing.getHandler() instanceof ZigBeeThingHandler)) {
            return null;
        }
        logger.debug("ZigBee Firmware Provider: Getting requested version of {}", thing.getUID());
        ZigBeeThingHandler zigbeeHandler = (ZigBeeThingHandler) thing.getHandler();
        if (zigbeeHandler == null) {
            logger.info("ZigBee Firmware Provider: No handler found for {}", thing.getUID());
            return null;
        }
        ZigBeeFirmwareVersion version = zigbeeHandler.getRequestedFirmwareVersion();
        logger.debug("ZigBee Firmware Provider: Requested version of {} is {}", thing.getUID(), version);
        return zigbeeHandler.getRequestedFirmwareVersion();
    }

    private Firmware getZigBeeFirmware(@NonNull ThingTypeUID thingTypeUID, DirectoryFileEntry directoryEntry) {
        return getZigBeeFirmware(thingTypeUID, directoryEntry, null);
    }

    private Firmware getZigBeeFirmware(@NonNull ThingTypeUID thingTypeUID, DirectoryFileEntry directoryEntry,
            InputStream inputStream) {
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
        if (!directoryEntry.getReleaseNotes().isEmpty()) {
            builder.withChangelog(directoryEntry.getReleaseNotes());
        }
        if (!directoryEntry.getMd5().isEmpty()) {
            builder.withMd5Hash(directoryEntry.getMd5());
        }
        if (!directoryEntry.getPrerequisiteVersion().isEmpty()) {
            builder.withPrerequisiteVersion(directoryEntry.getPrerequisiteVersion());
        }

        if (inputStream != null) {
            builder.withInputStream(inputStream);
        }

        Map<String, String> properties = new HashMap<>();

        if (directoryEntry.getFilesize() != null) {
            properties.put("Filesize", directoryEntry.getFilesize().toString());
        }
        if (directoryEntry.getFilename() != null) {
            properties.put("Filename", directoryEntry.getFilename());
        }

        builder.withProperties(properties);

        return builder.build();
    }

}