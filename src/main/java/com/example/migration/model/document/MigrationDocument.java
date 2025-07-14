package com.example.migration.model.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 遷移文檔類
 * MongoDB 中的遷移文檔結構
 */
@Document(collection = "migration_documents")
public class MigrationDocument {

    @Id
    private String id;

    private String sourceTable;

    private LocalDateTime migrationTime;

    private String version;

    private Map<String, Object> data;

    private String status = "MIGRATED";

    private String errorMessage;

    // Constructors
    public MigrationDocument() {}

    public MigrationDocument(String id, String sourceTable, Map<String, Object> data) {
        this.id = id;
        this.sourceTable = sourceTable;
        this.data = data;
        this.migrationTime = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }

    public LocalDateTime getMigrationTime() { return migrationTime; }
    public void setMigrationTime(LocalDateTime migrationTime) { this.migrationTime = migrationTime; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public String toString() {
        return "MigrationDocument{" +
                "id='" + id + '\'' +
                ", sourceTable='" + sourceTable + '\'' +
                ", migrationTime=" + migrationTime +
                ", version='" + version + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}