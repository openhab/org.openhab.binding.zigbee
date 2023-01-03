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
package org.openhab.binding.zigbee.handler;

import java.util.Objects;

/**
 * Holds the components of a firmware request
 *
 * @author Chris Jackson
 *
 */
public class ZigBeeFirmwareVersion {
    private final int manufacturerCode;
    private final int imageType;
    private final int fileVersion;

    /**
     * Creates an immutable class containing information describing a zigbee firmware version
     *
     * @param manufacturerCode
     * @param imageType
     * @param fileVersion
     */
    public ZigBeeFirmwareVersion(final int manufacturerCode, final int imageType, final int fileVersion) {
        this.manufacturerCode = manufacturerCode;
        this.imageType = imageType;
        this.fileVersion = fileVersion;
    }

    /**
     * @return the manufacturerCode
     */
    public int getManufacturerCode() {
        return manufacturerCode;
    }

    /**
     * @return the fileType
     */
    public int getFileType() {
        return imageType;
    }

    /**
     * @return the fileVersion
     */
    public int getFileVersion() {
        return fileVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageType, fileVersion, manufacturerCode);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ZigBeeFirmwareVersion other = (ZigBeeFirmwareVersion) obj;
        return imageType == other.imageType && fileVersion == other.fileVersion
                && manufacturerCode == other.manufacturerCode;
    }

    @Override
    public String toString() {
        return "ZigBeeFirmwareVersion [manufacturerCode=" + manufacturerCode + ", imageType=" + imageType
                + ", fileVersion=" + fileVersion + "]";
    }

}
