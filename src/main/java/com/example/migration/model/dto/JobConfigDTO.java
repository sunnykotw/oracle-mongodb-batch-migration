package com.example.migration.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 作業配置 DTO
 */
public class JobConfigDTO {

    @NotBlank(message = "作業名稱不能為空")
    private String name;

    private String description;

    @NotNull(message = "排程配置不能為空")
    private ScheduleConfig schedule;

    @NotNull(message = "資料來源配置不能為空")
    private SourceConfig source;

    @NotNull(message = "目標配置不能為空")
    private TargetConfig target;

    private ArchiveConfig archive;

    private Integer batchSize = 1000;

    private Integer chunkSize = 100;

    // Nested Classes
    public static class ScheduleConfig {
        @NotBlank(message = "Cron 表達式不能為空")
        private String cron;
        
        private boolean enabled = true;

        // Getters and Setters
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class SourceConfig {
        @NotNull(message = "Oracle 配置不能為空")
        private OracleConfig oracle;

        public OracleConfig getOracle() { return oracle; }
        public void setOracle(OracleConfig oracle) { this.oracle = oracle; }
    }

    public static class OracleConfig {
        @NotBlank(message = "Schema 名稱不能為空")
        private String owner;

        @NotBlank(message = "資料表名稱不能為空")
        private String table;

        @NotNull(message = "CLOB 欄位不能為空")
        private List<String> clobColumns;

        @NotNull(message = "主鍵欄位不能為空")
        private List<String> keyColumns;

        private String whereCondition;

        // Getters and Setters
        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }

        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }

        public List<String> getClobColumns() { return clobColumns; }
        public void setClobColumns(List<String> clobColumns) { this.clobColumns = clobColumns; }

        public List<String> getKeyColumns() { return keyColumns; }
        public void setKeyColumns(List<String> keyColumns) { this.keyColumns = keyColumns; }

        public String getWhereCondition() { return whereCondition; }
        public void setWhereCondition(String whereCondition) { this.whereCondition = whereCondition; }
    }

    public static class TargetConfig {
        @NotNull(message = "MongoDB 配置不能為空")
        private MongodbConfig mongodb;

        public MongodbConfig getMongodb() { return mongodb; }
        public void setMongodb(MongodbConfig mongodb) { this.mongodb = mongodb; }
    }

    public static class MongodbConfig {
        @NotBlank(message = "資料庫名稱不能為空")
        private String database;

        @NotBlank(message = "集合名稱不能為空")
        private String collection;

        private boolean changeCollection = true;

        // Getters and Setters
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }

        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }

        public boolean isChangeCollection() { return changeCollection; }
        public void setChangeCollection(boolean changeCollection) { this.changeCollection = changeCollection; }
    }

    public static class ArchiveConfig {
        private boolean enabled = false;
        private String targetTable;

        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    }

    // Main Class Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ScheduleConfig getSchedule() { return schedule; }
    public void setSchedule(ScheduleConfig schedule) { this.schedule = schedule; }

    public SourceConfig getSource() { return source; }
    public void setSource(SourceConfig source) { this.source = source; }

    public TargetConfig getTarget() { return target; }
    public void setTarget(TargetConfig target) { this.target = target; }

    public ArchiveConfig getArchive() { return archive; }
    public void setArchive(ArchiveConfig archive) { this.archive = archive; }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }
}