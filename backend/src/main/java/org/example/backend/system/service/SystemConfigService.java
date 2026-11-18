package org.example.backend.system.service;

import org.example.backend.system.dto.SystemConfigDTO;
import org.example.backend.system.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 系统参数配置：sys_config 按 key 存储，缺省值兜底 */
@Service
public class SystemConfigService {

    private static final String KEY_MODEL_MODE = "model_mode";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MAX_FILE_SIZE = "max_file_size";
    private static final String KEY_ALLOW_SUFFIX = "allow_suffix";

    private static final String DEFAULT_MODEL_MODE = "cloud";
    private static final int DEFAULT_MAX_FILE_SIZE = 10;
    private static final String DEFAULT_ALLOW_SUFFIX = "pdf,doc,docx,jpg,png";

    private final SysConfigMapper configMapper;

    public SystemConfigService(SysConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public Map<String, Object> get() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelMode", configMapper.select(KEY_MODEL_MODE)
                .filter(v -> !v.isBlank()).orElse(DEFAULT_MODEL_MODE));
        result.put("apiKey", configMapper.select(KEY_API_KEY).orElse(""));
        result.put("maxFileSize", configMapper.select(KEY_MAX_FILE_SIZE)
                .map(this::parseInt).orElse(DEFAULT_MAX_FILE_SIZE));
        result.put("allowSuffix", configMapper.select(KEY_ALLOW_SUFFIX)
                .filter(v -> !v.isBlank()).orElse(DEFAULT_ALLOW_SUFFIX));
        return result;
    }

    public void save(SystemConfigDTO dto) {
        String modelMode = dto.getModelMode() == null || dto.getModelMode().isBlank()
                ? DEFAULT_MODEL_MODE : dto.getModelMode().trim();
        String apiKey = dto.getApiKey() == null ? "" : dto.getApiKey().trim();
        int maxFileSize = dto.getMaxFileSize() == null || dto.getMaxFileSize() < 1
                ? DEFAULT_MAX_FILE_SIZE : dto.getMaxFileSize();
        String allowSuffix = dto.getAllowSuffix() == null || dto.getAllowSuffix().isBlank()
                ? DEFAULT_ALLOW_SUFFIX : dto.getAllowSuffix().trim();

        configMapper.upsert(KEY_MODEL_MODE, modelMode);
        configMapper.upsert(KEY_API_KEY, apiKey);
        configMapper.upsert(KEY_MAX_FILE_SIZE, String.valueOf(maxFileSize));
        configMapper.upsert(KEY_ALLOW_SUFFIX, allowSuffix);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_FILE_SIZE; // 脏数据按默认值处理
        }
    }
}
