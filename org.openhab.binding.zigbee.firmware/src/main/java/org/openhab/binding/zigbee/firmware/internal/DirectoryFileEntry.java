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
package org.openhab.binding.zigbee.firmware.internal;

import java.util.List;

import org.openhab.binding.zigbee.handler.ZigBeeFirmwareVersion;

/**
 * Contains the information describing a firmware directory entry
 *
 * @author Chris Jackson
 *
 */
public class DirectoryFileEntry {
    private Integer manufacturerCode;
    private Integer imageType;
    private Integer fileVersion;
    private Integer fileSize;
    private String sha512;
    private List<String> manufacturerName;
    private String modelId;
    private String url;

    private String thingTypeUid;
    private String prerequisiteVersion;
    private String model;
    private String vendor;
    private String description;
    private String md5;

    /**
     * @return the fileSize
     */
    public Integer getFileSize() {
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
     * @return the filesize
     */
    public Integer getFilesize() {
        return fileSize;
    }

    /**
     * @param filesize the filesize to set
     */
    public void setFilesize(Integer filesize) {
        this.fileSize = filesize;
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
        return description == null ? "" : description;
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
     * @param manufacturerName the manufacturerName to set
     */
    public void setManufacturerName(List<String> manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    /**
     * @return the modelId
     */
    public String getModelId() {
        return modelId;
    }

    /**
     * @param modelId the modelId to set
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param url the url to set
     */
    public void setUrl(String url) {
        this.url = url;
    }

}
