package org.example.backend.system.dto;

/** 系统参数配置入参：allowSuffix 逗号分隔，如 pdf,doc,docx,jpg,png */
public class SystemConfigDTO {

    private String modelMode;
    private String apiKey;
    private Integer maxFileSize;
    private String allowSuffix;

    public String getModelMode() { return modelMode; }
    public void setModelMode(String modelMode) { this.modelMode = modelMode; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Integer getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(Integer maxFileSize) { this.maxFileSize = maxFileSize; }

    public String getAllowSuffix() { return allowSuffix; }
    public void setAllowSuffix(String allowSuffix) { this.allowSuffix = allowSuffix; }
}
