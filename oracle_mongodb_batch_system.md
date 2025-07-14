# Oracle to MongoDB 批次作業系統架構設計

## 1. 專案概述

### 1.1 系統目標
- 建立彈性的批次作業系統，將Oracle CLOB資料遷移到MongoDB
- 提供可配置的資料映射機制
- 支援排程執行和監控功能
- 提供Web管理介面

### 1.2 技術堆疊
- **後端**: Spring Boot 3.x, Spring Batch, Spring Data JPA, Spring Data MongoDB
- **資料庫**: Oracle Database, MongoDB
- **建構工具**: Maven
- **前端**: React
- **監控**: Spring Boot Actuator, Micrometer
- **排程**: Spring Scheduler / Quartz

## 2. 專案結構

```
oracle-mongodb-batch-migration/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/migration/
│   │   │       ├── MigrationApplication.java
│   │   │       ├── config/
│   │   │       │   ├── BatchConfig.java
│   │   │       │   ├── DatabaseConfig.java
│   │   │       │   ├── MongoConfig.java
│   │   │       │   ├── SchedulerConfig.java
│   │   │       │   └── SecurityConfig.java
│   │   │       ├── batch/
│   │   │       │   ├── job/
│   │   │       │   │   ├── MigrationJobConfig.java
│   │   │       │   │   └── JobRegistryConfig.java
│   │   │       │   ├── step/
│   │   │       │   │   ├── reader/
│   │   │       │   │   │   └── OracleClobReader.java
│   │   │       │   │   ├── processor/
│   │   │       │   │   │   └── DataTransformProcessor.java
│   │   │       │   │   └── writer/
│   │   │       │   │       ├── MongoDocumentWriter.java
│   │   │       │   │       └── OracleArchiveWriter.java
│   │   │       │   └── listener/
│   │   │       │       ├── JobExecutionListener.java
│   │   │       │       └── StepExecutionListener.java
│   │   │       ├── model/
│   │   │       │   ├── entity/
│   │   │       │   │   ├── OracleEntity.java
│   │   │       │   │   └── JobExecutionHistory.java
│   │   │       │   ├── document/
│   │   │       │   │   └── MigrationDocument.java
│   │   │       │   └── dto/
│   │   │       │       ├── JobConfigDTO.java
│   │   │       │       ├── JobExecutionDTO.java
│   │   │       │       └── MigrationStatusDTO.java
│   │   │       ├── service/
│   │   │       │   ├── JobManagementService.java
│   │   │       │   ├── ConfigurationService.java
│   │   │       │   ├── MonitoringService.java
│   │   │       │   └── DeadLetterQueueService.java
│   │   │       ├── repository/
│   │   │       │   ├── oracle/
│   │   │       │   │   ├── OracleRepository.java
│   │   │       │   │   └── JobExecutionHistoryRepository.java
│   │   │       │   └── mongodb/
│   │   │       │       └── MigrationDocumentRepository.java
│   │   │       ├── controller/
│   │   │       │   ├── BatchController.java
│   │   │       │   ├── JobController.java
│   │   │       │   └── MonitoringController.java
│   │   │       └── exception/
│   │   │           ├── GlobalExceptionHandler.java
│   │   │           └── custom/
│   │   │               ├── MigrationException.java
│   │   │               └── ConfigurationException.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── job-configs/
│   │       │   ├── default-migration-job.yml
│   │       │   └── custom-migration-job.yml
│   │       └── static/
│   │           └── react-app/
│   │               ├── build/
│   │               └── src/
│   └── test/
└── frontend/
    └── migration-admin/
        ├── package.json
        ├── public/
        └── src/
            ├── components/
            │   ├── JobManager/
            │   ├── JobExecution/
            │   ├── Monitoring/
            │   └── Configuration/
            ├── services/
            ├── utils/
            └── App.js
```

## 3. 核心架構組件

### 3.1 配置管理層
#### 3.1.1 BatchConfig
- 配置Spring Batch基本設定
- 定義JobRepository、JobLauncher、TaskExecutor
- 設定事務管理器

#### 3.1.2 DatabaseConfig
- Oracle資料庫連接配置
- 連接池設定
- JPA實體映射

#### 3.1.3 MongoConfig
- MongoDB連接配置
- 資料庫和集合映射
- 索引設定

#### 3.1.4 SchedulerConfig
- Quartz或Spring Scheduler配置
- Job觸發器設定
- 排程管理

### 3.2 批次處理層
#### 3.2.1 Job配置
- **MigrationJobConfig**: 主要遷移Job配置
- **JobRegistryConfig**: Job註冊和管理
- 支援動態Job創建和配置

#### 3.2.2 Step組件
##### Reader (OracleClobReader)
- 從Oracle讀取CLOB資料
- 支援分頁查詢
- 根據配置檔案動態生成SQL
- 處理大型CLOB資料

##### Processor (DataTransformProcessor)
- 資料轉換和映射
- CLOB資料格式處理
- 欄位映射和驗證
- 資料清洗和標準化

##### Writer (MongoDocumentWriter & OracleArchiveWriter)
- **MongoDocumentWriter**: 寫入MongoDB
- **OracleArchiveWriter**: 選擇性封存到Oracle其他表
- 批次寫入優化
- 錯誤處理和回復

#### 3.2.3 監聽器
- **JobExecutionListener**: Job執行狀態監控
- **StepExecutionListener**: Step執行統計
- 錯誤記錄和通知

### 3.3 資料模型層
#### 3.3.1 Entity (Oracle)
- **OracleEntity**: 動態實體映射
- **JobExecutionHistory**: 執行歷史記錄

#### 3.3.2 Document (MongoDB)
- **MigrationDocument**: 遷移後的文檔結構
- 支援動態欄位映射

#### 3.3.3 DTO
- **JobConfigDTO**: Job配置資料傳輸
- **JobExecutionDTO**: 執行狀態資料
- **MigrationStatusDTO**: 遷移狀態資訊

### 3.4 服務層
#### 3.4.1 JobManagementService
- Job的創建、啟動、停止
- Job參數管理
- 排程管理

#### 3.4.2 ConfigurationService
- 配置檔案解析
- 動態配置更新
- 配置驗證

#### 3.4.3 MonitoringService
- 執行狀態監控
- 效能指標收集
- 告警機制

#### 3.4.4 DeadLetterQueueService
- 失敗資料處理
- 重試機制
- 錯誤補償

### 3.5 控制器層
#### 3.5.1 BatchController
- 批次作業管理API
- Job觸發和控制

#### 3.5.2 JobController
- Job配置管理
- Job執行歷史查詢

#### 3.5.3 MonitoringController
- 監控資料API
- 系統健康檢查

## 4. 配置檔案設計

### 4.1 Job配置檔案結構
```yaml
job:
  name: "oracle-to-mongodb-migration"
  description: "Oracle CLOB to MongoDB migration job"
  schedule:
    cron: "0 0 2 * * ?"  # 每天凌晨2點執行
    enabled: true
  
  source:
    oracle:
      owner: "SCHEMA_NAME"
      table: "SOURCE_TABLE"
      clob_columns:
        - "CLOB_COLUMN_1"
        - "CLOB_COLUMN_2"
      key_columns:
        - "ID"
        - "BUSINESS_KEY"
      where_condition: "STATUS = 'ACTIVE' AND CREATED_DATE > SYSDATE - 30"
      
  target:
    mongodb:
      database: "target_database"
      collection: "target_collection"
      change_collection: true
      
  archive:
    enabled: true
    target_table: "ARCHIVE_TABLE"
    
  batch_size: 1000
  chunk_size: 100
```

### 4.2 應用程式配置
```yaml
spring:
  datasource:
    oracle:
      url: jdbc:oracle:thin:@localhost:1521:xe
      username: ${ORACLE_USERNAME}
      password: ${ORACLE_PASSWORD}
  
  data:
    mongodb:
      uri: mongodb://localhost:27017/migration_db
      
  batch:
    job:
      enabled: false
    jdbc:
      initialize-schema: always
      
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,batch
  endpoint:
    health:
      show-details: always
```

## 5. 監控和錯誤處理

### 5.1 Spring Boot Actuator整合
- Health Check端點
- Metrics收集
- Batch執行狀態監控
- 自定義指標

### 5.2 Dead Letter Queue機制
- 失敗資料隔離
- 重試策略
- 錯誤分類和處理
- 手動介入機制

### 5.3 日誌和追蹤
- 結構化日誌
- 執行軌跡追蹤
- 效能分析
- 錯誤堆棧收集

## 6. 前端管理介面

### 6.1 React應用程式結構
- **JobManager**: Job管理介面
- **JobExecution**: 執行狀態監控
- **Monitoring**: 系統監控儀表板
- **Configuration**: 配置管理

### 6.2 功能模組
#### 6.2.1 Job管理
- Job列表和狀態
- 手動觸發Job
- Job配置編輯
- 執行歷史查詢

#### 6.2.2 監控儀表板
- 即時執行狀態
- 效能指標圖表
- 錯誤統計
- 系統健康狀態

#### 6.2.3 配置管理
- 配置檔案編輯
- 配置驗證
- 配置版本管理
- 配置部署

## 7. 部署和維運

### 7.1 Maven配置
- 多環境profile配置
- 依賴管理
- 建構優化
- 測試配置

### 7.2 容器化部署
- Docker容器配置
- 環境變數管理
- 健康檢查
- 日誌輸出

### 7.3 監控和告警
- JMX監控
- 自定義指標
- 告警規則
- 運維介面

## 8. 安全性考量

### 8.1 資料庫安全
- 連接加密
- 認證和授權
- 資料遮罩
- 審計日誌

### 8.2 應用程式安全
- API認證
- 角色權限管理
- 輸入驗證
- 安全標頭

## 9. 效能優化

### 9.1 批次處理優化
- 分頁查詢
- 並行處理
- 記憶體管理
- 資料庫連接池

### 9.2 MongoDB優化
- 索引策略
- 寫入批次化
- 連接池配置
- 分片支援

## 10. 測試策略

### 10.1 單元測試
- Service層測試
- Repository層測試
- Batch組件測試
- 工具類測試

### 10.2 整合測試
- 資料庫整合測試
- Batch Job測試
- API整合測試
- 端到端測試

這個架構設計提供了一個完整的、可擴展的批次作業系統，支援Oracle到MongoDB的資料遷移，並包含了監控、錯誤處理和管理介面等企業級功能。