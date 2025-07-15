package com.example.migration.service;

import com.example.migration.model.dto.JobConfigDTO;
import com.example.migration.exception.custom.ConfigurationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

    @Value("${migration.config.path:job-configs/}")
    private String configPath;

    @Value("${migration.config.reload.enabled:true}")
    private boolean reloadEnabled;

    @Value("${migration.config.validation.enabled:true}")
    private boolean validationEnabled;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, JobConfigDTO> jobConfigs = new ConcurrentHashMap<>();
    private final Map<String, Long> configLastModified = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        logger.info("Initializing ConfigurationService");
        loadAllConfigurations();
    }

    /**
     * 載入所有配置檔案
     */
    public void loadAllConfigurations() {
        logger.info("Loading all job configurations from: {}", configPath);
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:" + configPath + "*.yml");
            
            for (Resource resource : resources) {
                try {
                    loadConfiguration(resource);
                } catch (Exception e) {
                    logger.error("Error loading configuration from {}: {}", resource.getFilename(), e.getMessage());
                }
            }
            
            logger.info("Loaded {} job configurations", jobConfigs.size());
            
        } catch (IOException e) {
            logger.error("Error loading configurations", e);
            throw new ConfigurationException("Error loading configurations", e);
        }
    }

    /**
     * 載入單個配置檔案
     */
    private void loadConfiguration(Resource resource) throws IOException {
        logger.debug("Loading configuration from: {}", resource.getFilename());
        
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode configNode = yamlMapper.readTree(inputStream);
            JobConfigDTO jobConfig = parseJobConfig(configNode);
            
            if (validationEnabled) {
                validateJobConfig(jobConfig);
            }
            
            jobConfigs.put(jobConfig.getName(), jobConfig);
            configLastModified.put(jobConfig.getName(), System.currentTimeMillis());
            
            logger.info("Loaded job configuration: {}", jobConfig.getName());
            
        } catch (Exception e) {
            logger.error("Error parsing configuration from {}: {}", resource.getFilename(), e.getMessage());
            throw new ConfigurationException("Error parsing configuration: " + resource.getFilename(), e);
        }
    }

    /**
     * 獲取Job配置
     */
    public JobConfigDTO getJobConfig(String jobName) {
        if (reloadEnabled) {
            checkAndReloadConfiguration(jobName);
        }
        
        JobConfigDTO config = jobConfigs.get(jobName);
        if (config == null) {
            logger.warn("Job configuration not found: {}", jobName);
            throw new ConfigurationException("Job configuration not found: " + jobName);
        }
        
        return deepCopy(config);
    }

    /**
     * 獲取所有Job配置
     */
    public Map<String, JobConfigDTO> getAllJobConfigs() {
        if (reloadEnabled) {
            loadAllConfigurations();
        }
        
        Map<String, JobConfigDTO> allConfigs = new HashMap<>();
        for (Map.Entry<String, JobConfigDTO> entry : jobConfigs.entrySet()) {
            allConfigs.put(entry.getKey(), deepCopy(entry.getValue()));
        }
        
        return allConfigs;
    }

    /**
     * 保存Job配置
     */
    public void saveJobConfig(JobConfigDTO jobConfig) {
        logger.info("Saving job configuration: {}", jobConfig.getName());
        
        if (validationEnabled) {
            validateJobConfig(jobConfig);
        }
        
        try {
            // 轉換為YAML字串
            String yamlContent = yamlMapper.writeValueAsString(createConfigMap(jobConfig));
            
            // 寫入檔案
            Path configFilePath = Paths.get(configPath, jobConfig.getName() + ".yml");
            Files.write(configFilePath, yamlContent.getBytes(), 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // 更新記憶體中的配置
            jobConfigs.put(jobConfig.getName(), jobConfig);
            configLastModified.put(jobConfig.getName(), System.currentTimeMillis());
            
            logger.info("Job configuration saved successfully: {}", jobConfig.getName());
            
        } catch (IOException e) {
            logger.error("Error saving job configuration: {}", jobConfig.getName(), e);
            throw new ConfigurationException("Error saving job configuration: " + jobConfig.getName(), e);
        }
    }

    /**
     * 刪除Job配置
     */
    public void deleteJobConfig(String jobName) {
        logger.info("Deleting job configuration: {}", jobName);
        
        try {
            Path configFilePath = Paths.get(configPath, jobName + ".yml");
            if (Files.exists(configFilePath)) {
                Files.delete(configFilePath);
            }
            
            jobConfigs.remove(jobName);
            configLastModified.remove(jobName);
            
            logger.info("Job configuration deleted successfully: {}", jobName);
            
        } catch (IOException e) {
            logger.error("Error deleting job configuration: {}", jobName, e);
            throw new ConfigurationException("Error deleting job configuration: " + jobName, e);
        }
    }

    /**
     * 驗證Job配置
     */
    public void validateJobConfig(JobConfigDTO jobConfig) {
        List<String> errors = new ArrayList<>();
        
        // 基本驗證
        if (jobConfig.getName() == null || jobConfig.getName().trim().isEmpty()) {
            errors.add("Job name is required");
        }
        
        if (jobConfig.getDescription() == null || jobConfig.getDescription().trim().isEmpty()) {
            errors.add("Job description is required");
        }
        
        // 來源配置驗證
        if (jobConfig.getSource() == null) {
            errors.add("Source configuration is required");
        } else {
            validateSourceConfig(jobConfig.getSource(), errors);
        }
        
        // 目標配置驗證
        if (jobConfig.getTarget() == null) {
            errors.add("Target configuration is required");
        } else {
            validateTargetConfig(jobConfig.getTarget(), errors);
        }
        
        // 排程配置驗證
        if (jobConfig.getSchedule() != null) {
            validateScheduleConfig(jobConfig.getSchedule(), errors);
        }
        
        // 批次配置驗證
        if (jobConfig.getBatchSize() != null && jobConfig.getBatchSize() <= 0) {
            errors.add("Batch size must be greater than 0");
        }
        
        if (jobConfig.getChunkSize() != null && jobConfig.getChunkSize() <= 0) {
            errors.add("Chunk size must be greater than 0");
        }
        
        if (!errors.isEmpty()) {
            String errorMessage = "Job configuration validation failed: " + String.join(", ", errors);
            logger.error(errorMessage);
            throw new ConfigurationException(errorMessage);
        }
        
        logger.debug("Job configuration validation passed: {}", jobConfig.getName());
    }

    /**
     * 檢查並重新載入配置
     */
    private void checkAndReloadConfiguration(String jobName) {
        try {
            Path configFilePath = Paths.get(configPath, jobName + ".yml");
            if (Files.exists(configFilePath)) {
                long lastModified = Files.getLastModifiedTime(configFilePath).toMillis();
                Long cachedModified = configLastModified.get(jobName);
                
                if (cachedModified == null || lastModified > cachedModified) {
                    logger.info("Reloading configuration for job: {}", jobName);
                    ClassPathResource resource = new ClassPathResource(configPath + jobName + ".yml");
                    loadConfiguration(resource);
                }
            }
        } catch (IOException e) {
            logger.warn("Error checking configuration modification time for job: {}", jobName, e);
        }
    }

    /**
     * 解析Job配置
     */
    private JobConfigDTO parseJobConfig(JsonNode configNode) {
        try {
            JsonNode jobNode = configNode.get("job");
            if (jobNode == null) {
                throw new ConfigurationException("Missing 'job' node in configuration");
            }
            
            JobConfigDTO jobConfig = new JobConfigDTO();
            
            // 基本資訊
            jobConfig.setName(jobNode.get("name").asText());
            jobConfig.setDescription(jobNode.get("description").asText());
            
            // 排程配置
            JsonNode scheduleNode = jobNode.get("schedule");
            if (scheduleNode != null) {
                Map<String, Object> schedule = new HashMap<>();
                schedule.put("cron", scheduleNode.get("cron").asText());
                schedule.put("enabled", scheduleNode.get("enabled").asBoolean());
                jobConfig.setSchedule(schedule);
            }
            
            // 來源配置
            JsonNode sourceNode = jobNode.get("source");
            if (sourceNode != null) {
                Map<String, Object> source = yamlMapper.convertValue(sourceNode, Map.class);
                jobConfig.setSource(source);
            }
            
            // 目標配置
            JsonNode targetNode = jobNode.get("target");
            if (targetNode != null) {
                Map<String, Object> target = yamlMapper.convertValue(targetNode, Map.class);
                jobConfig.setTarget(target);
            }
            
            // 封存配置
            JsonNode archiveNode = jobNode.get("archive");
            if (archiveNode != null) {
                Map<String, Object> archive = yamlMapper.convertValue(archiveNode, Map.class);
                jobConfig.setArchive(archive);
            }
            
            // 批次配置
            if (jobNode.has("batch_size")) {
                jobConfig.setBatchSize(jobNode.get("batch_size").asInt());
            }
            if (jobNode.has("chunk_size")) {
                jobConfig.setChunkSize(jobNode.get("chunk_size").asInt());
            }
            
            return jobConfig;
            
        } catch (Exception e) {
            logger.error("Error parsing job configuration", e);
            throw new ConfigurationException("Error parsing job configuration", e);
        }
    }

    /**
     * 創建配置映射
     */
    private Map<String, Object> createConfigMap(JobConfigDTO jobConfig) {
        Map<String, Object> configMap = new HashMap<>();
        Map<String, Object> jobMap = new HashMap<>();
        
        jobMap.put("name", jobConfig.getName());
        jobMap.put("description", jobConfig.getDescription());
        
        if (jobConfig.getSchedule() != null) {
            jobMap.put("schedule", jobConfig.getSchedule());
        }
        
        if (jobConfig.getSource() != null) {
            jobMap.put("source", jobConfig.getSource());
        }
        
        if (jobConfig.getTarget() != null) {
            jobMap.put("target", jobConfig.getTarget());
        }
        
        if (jobConfig.getArchive() != null) {
            jobMap.put("archive", jobConfig.getArchive());
        }
        
        if (jobConfig.getBatchSize() != null) {
            jobMap.put("batch_size", jobConfig.getBatchSize());
        }
        
        if (jobConfig.getChunkSize() != null) {
            jobMap.put("chunk_size", jobConfig.getChunkSize());
        }
        
        configMap.put("job", jobMap);
        return configMap;
    }

    /**
     * 驗證來源配置
     */
    private void validateSourceConfig(Map<String, Object> source, List<String> errors) {
        Map<String, Object> oracle = (Map<String, Object>) source.get("oracle");
        if (oracle == null) {
            errors.add("Oracle source configuration is required");
            return;
        }
        
        if (oracle.get("owner") == null || oracle.get("owner").toString().trim().isEmpty()) {
            errors.add("Oracle owner is required");
        }
        
        if (oracle.get("table") == null || oracle.get("table").toString().trim().isEmpty()) {
            errors.add("Oracle table is required");
        }
        
        List<String> clobColumns = (List<String>) oracle.get("clob_columns");
        if (clobColumns == null || clobColumns.isEmpty()) {
            errors.add("At least one CLOB column is required");
        }
        
        List<String> keyColumns = (List<String>) oracle.get("key_columns");
        if (keyColumns == null || keyColumns.isEmpty()) {
            errors.add("At least one key column is required");
        }
    }

    /**
     * 驗證目標配置
     */
    private void validateTargetConfig(Map<String, Object> target, List<String> errors) {
        Map<String, Object> mongodb = (Map<String, Object>) target.get("mongodb");
        if (mongodb == null) {
            errors.add("MongoDB target configuration is required");
            return;
        }
        
        if (mongodb.get("database") == null || mongodb.get("database").toString().trim().isEmpty()) {
            errors.add("MongoDB database is required");
        }
        
        if (mongodb.get("collection") == null || mongodb.get("collection").toString().trim().isEmpty()) {
            errors.add("MongoDB collection is required");
        }
    }

    /**
     * 驗證排程配置
     */
    private void validateScheduleConfig(Map<String, Object> schedule, List<String> errors) {
        if (schedule.get("cron") == null || schedule.get("cron").toString().trim().isEmpty()) {
            errors.add("Cron expression is required when schedule is configured");
        } else {
            // 簡單的cron表達式驗證
            String cronExpression = schedule.get("cron").toString();
            if (!isValidCronExpression(cronExpression)) {
                errors.add("Invalid cron expression: " + cronExpression);
            }
        }
    }

    /**
     * 驗證Cron表達式
     */
    private boolean isValidCronExpression(String cronExpression) {
        // 簡單的cron表達式驗證
        String[] parts = cronExpression.split("\\s+");
        return parts.length == 6 || parts.length == 7;
    }

    /**
     * 深拷貝配置對象
     */
    private JobConfigDTO deepCopy(JobConfigDTO original) {
        try {
            String json = yamlMapper.writeValueAsString(original);
            return yamlMapper.readValue(json, JobConfigDTO.class);
        } catch (Exception e) {
            logger.error("Error creating deep copy of job configuration", e);
            throw new ConfigurationException("Error creating deep copy of job configuration", e);
        }
    }

    /**
     * 獲取配置文件路徑
     */
    public String getConfigPath() {
        return configPath;
    }

    /**
     * 設置配置文件路徑
     */
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    /**
     * 檢查配置是否存在
     */
    public boolean configExists(String jobName) {
        return jobConfigs.containsKey(jobName);
    }

    /**
     * 獲取配置最後修改時間
     */
    public Long getConfigLastModified(String jobName) {
        return configLastModified.get(jobName);
    }

    /**
     * 重新載入特定配置
     */
    public void reloadConfiguration(String jobName) {
        logger.info("Reloading configuration for job: {}", jobName);
        
        try {
            ClassPathResource resource = new ClassPathResource(configPath + jobName + ".yml");
            if (resource.exists()) {
                loadConfiguration(resource);
                logger.info("Configuration reloaded successfully for job: {}", jobName);
            } else {
                logger.warn("Configuration file not found for job: {}", jobName);
            }
        } catch (Exception e) {
            logger.error("Error reloading configuration for job: {}", jobName, e);
            throw new ConfigurationException("Error reloading configuration for job: " + jobName, e);
        }
    }

    /**
     * 獲取配置統計資訊
     */
    public Map<String, Object> getConfigurationStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        statistics.put("totalConfigurations", jobConfigs.size());
        statistics.put("validationEnabled", validationEnabled);
        statistics.put("reloadEnabled", reloadEnabled);
        statistics.put("configPath", configPath);
        
        // 按狀態統計
        Map<String, Integer> statusCounts = new HashMap<>();
        for (JobConfigDTO config : jobConfigs.values()) {
            Map<String, Object> schedule = config.getSchedule();
            if (schedule != null) {
                Boolean enabled = (Boolean) schedule.get("enabled");
                String status = (enabled != null && enabled) ? "enabled" : "disabled";
                statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
            } else {
                statusCounts.put("no_schedule", statusCounts.getOrDefault("no_schedule", 0) + 1);
            }
        }
        statistics.put("statusCounts", statusCounts);
        
        return statistics;
    }
}