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
package org.openhab.binding.zigbee.firmware.internal;

import java.util.List;

import org.openhab.binding.zigbee.handler.ZigBeeFirmwareVersion;

/**
 * Contains the information describing a firmware directory entry
 *
 * @author Chris Jackson
 *
 */
public class DirectoryFileEntry extends DirectoryEntry {
    private String fileName;
    private Integer manufacturerCode;
    private Integer imageType;
    private Integer fileVersion;
    private Integer fileSize;
    private String sha512;
    private List<String> manufacturerName;
    private String modelId;
    private String url;

    private String otaHeaderString;

    private Boolean force;
    private Integer hardwareVersionMin;
    private Integer hardwareVersionMax;
    private Integer minFileVersion;
    private Integer maxFileVersion;
    private String originalUrl;
    private String releaseNotes;

    // OH internal
    private String thingTypeUid;
    private String prerequisiteVersion;
    private String model;
    private String vendor;
    private String description;
    private String md5;

    /**
     * @return the filename
     */
    public String getFilename() {
        return fileName;
    }

    /**
     * @return the fileSize
     */
    public Integer getFilesize() {
        return fileSize;
    }

    /**
     * @return the fileVersion
     */
    public Integer getFileVersion() {
        return fileVersion;
    }

    /**
     * @return the manufacturerCode
     */
    public Integer getManufacturerCode() {
        return manufacturerCode;
    }

    /**
     * @return the imageType
     */
    public Integer getImageType() {
        return imageType;
    }

    /**
     * @return the sha512
     */
    public String getSha512() {
        return sha512;
    }

    /**
     * @return the thingTypeUid
     */
    public String getThingTypeUid() {
        return thingTypeUid == null ? "" : thingTypeUid;
    }

    /**
     * @return the version
     */
    public Integer getVersion() {
        return fileVersion;
    }

    /**
     * @return the model
     */
    public String getModel() {
        return model == null ? "" : model;
    }

    /**
     * @return the vendor
     */
    public String getVendor() {
        return vendor == null ? "" : vendor;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        if (description != null) {
            return description;
        }
        if (otaHeaderString != null) {
            return otaHeaderString;
        }
        return null;
    }

    /**
     * @param md5 the md5 to set
     */
    public void setMd5(String md5) {
        this.md5 = md5;
    }

    /**
     * @return the hash
     */
    public String getMd5() {
        return md5 == null ? "" : md5;
    }

    /**
     * @return the prerequisiteVersion
     */
    public String getPrerequisiteVersion() {
        return prerequisiteVersion == null ? "" : prerequisiteVersion;
    }

    public ZigBeeFirmwareVersion getFirmwareVersion() {
        return new ZigBeeFirmwareVersion(manufacturerCode, imageType, fileVersion);
    }

    /**
     * @return the manufacturerName
     */
    public List<String> getManufacturerName() {
        return manufacturerName;
    }

    /**
     * @return the modelId
     */
    public String getModelId() {
        return modelId == null ? "" : modelId;
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return the otaHeaderString
     */
    public String getOtaHeaderString() {
        return otaHeaderString;
    }

    /**
     * @return the force
     */
    public Boolean getForce() {
        return force == null ? Boolean.FALSE : force;
    }

    /**
     * @return the hardwareVersionMin
     */
    public Integer getHardwareVersionMin() {
        return hardwareVersionMin;
    }

    /**
     * @return the hardwareVersionMax
     */
    public Integer getHardwareVersionMax() {
        return hardwareVersionMax;
    }

    /**
     * @return the minFileVersion
     */
    public Integer getMinFileVersion() {
        return minFileVersion;
    }

    /**
     * @return the maxFileVersion
     */
    public Integer getMaxFileVersion() {
        return maxFileVersion;
    }

    /**
     * @return the originalUrl
     */
    public String getOriginalUrl() {
        return originalUrl;
    }

    /**
     * @return the releaseNotes
     */
    public String getReleaseNotes() {
        return releaseNotes == null ? "" : releaseNotes;
    }
}
