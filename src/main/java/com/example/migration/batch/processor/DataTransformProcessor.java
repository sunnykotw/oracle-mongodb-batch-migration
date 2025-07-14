package com.example.migration.batch.processor;

import com.example.migration.model.document.MigrationDocument;
import com.example.migration.model.entity.OracleEntity;
import com.example.migration.service.ConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 資料轉換處理器
 * 將 Oracle 實體轉換為 MongoDB 文檔
 */
@Component
public class DataTransformProcessor implements ItemProcessor<OracleEntity, MigrationDocument> {

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public MigrationDocument process(OracleEntity item) throws Exception {
        if (item == null) {
            return null;
        }

        MigrationDocument document = new MigrationDocument();
        
        // 設置基本資訊
        document.setSourceTable(getSourceTableName());
        document.setMigrationTime(LocalDateTime.now());
        document.setVersion("1.0");
        
        // 轉換資料
        Map<String, Object> data = transformData(item);
        document.setData(data);
        
        // 生成文檔 ID
        document.setId(generateDocumentId(item));
        
        return document;
    }

    private String generateDocumentId(OracleEntity entity) {
        var config = configurationService.getCurrentJobConfig();
        var keyColumns = config.getSource().getOracle().getKeyColumns();
        
        StringBuilder id = new StringBuilder();
        for (String keyColumn : keyColumns) {
            Object value = entity.getField(keyColumn);
            if (value != null) {
                id.append(value.toString()).append("_");
            }
        }
        
        // 移除最後的底線
        if (id.length() > 0) {
            id.setLength(id.length() - 1);
        }
        
        return id.toString();
    }

    private String getSourceTableName() {
        var config = configurationService.getCurrentJobConfig();
        return config.getSource().getOracle().getTable();
    }

    private Map<String, Object> transformData(OracleEntity entity) {
        Map<String, Object> data = new HashMap<>();
        
        // 複製所有欄位
        entity.getFields().forEach((key, value) -> {
            // 處理特殊資料類型
            if (value instanceof String) {
                String strValue = (String) value;
                // 如果是 JSON 字符串，嘗試解析
                if (isJsonString(strValue)) {
                    try {
                        Object jsonObj = objectMapper.readValue(strValue, Object.class);
                        data.put(key, jsonObj);
                    } catch (Exception e) {
                        // 解析失敗，保持原字符串
                        data.put(key, strValue);
                    }
                } else {
                    data.put(key, strValue);
                }
            } else {
                data.put(key, value);
            }
        });
        
        return data;
    }

    private boolean isJsonString(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = str.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}