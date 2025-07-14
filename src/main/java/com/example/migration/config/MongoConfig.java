package com.example.migration.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB 配置類
 * 配置 MongoDB 連接和相關設定
 */
@Configuration
@EnableMongoRepositories(
    basePackages = "com.example.migration.repository.mongodb",
    mongoTemplateRef = "mongoTemplate"
)
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Value("${mongodb.connection.max-pool-size:100}")
    private int maxPoolSize;

    @Value("${mongodb.connection.min-pool-size:10}")
    private int minPoolSize;

    @Value("${mongodb.connection.max-wait-time:30000}")
    private int maxWaitTime;

    @Value("${mongodb.connection.connect-timeout:30000}")
    private int connectTimeout;

    @Value("${mongodb.connection.socket-timeout:30000}")
    private int socketTimeout;

    @Value("${mongodb.connection.server-selection-timeout:30000}")
    private int serverSelectionTimeout;

    @Value("${mongodb.connection.max-connection-idle-time:600000}")
    private int maxConnectionIdleTime;

    @Value("${mongodb.connection.max-connection-life-time:1800000}")
    private int maxConnectionLifeTime;

    /**
     * 返回資料庫名稱
     */
    @Override
    protected String getDatabaseName() {
        return database;
    }

    /**
     * 配置 MongoDB 客戶端
     */
    @Override
    @Bean
    public MongoClient mongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToConnectionPoolSettings(builder -> 
                    builder.maxSize(maxPoolSize)
                           .minSize(minPoolSize)
                           .maxWaitTime(maxWaitTime, TimeUnit.MILLISECONDS)
                           .maxConnectionIdleTime(maxConnectionIdleTime, TimeUnit.MILLISECONDS)
                           .maxConnectionLifeTime(maxConnectionLifeTime, TimeUnit.MILLISECONDS)
                )
                .applyToSocketSettings(builder -> 
                    builder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                           .readTimeout(socketTimeout, TimeUnit.MILLISECONDS)
                )
                .applyToServerSettings(builder -> 
                    builder.minHeartbeatFrequency(500, TimeUnit.MILLISECONDS)
                           .heartbeatFrequency(10000, TimeUnit.MILLISECONDS)
                )
                .applyToClusterSettings(builder -> 
                    builder.serverSelectionTimeout(serverSelectionTimeout, TimeUnit.MILLISECONDS)
                )
                .build();
        
        return MongoClients.create(settings);
    }

    /**
     * 配置 MongoDB Template
     */
    @Bean
    public MongoTemplate mongoTemplate() {
        MongoTemplate template = new MongoTemplate(mongoClient(), getDatabaseName());
        
        // 配置自定義轉換器
        MappingMongoConverter converter = (MappingMongoConverter) template.getConverter();
        
        // 移除 _class 字段
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        
        // 配置映射上下文
        MongoMappingContext mappingContext = (MongoMappingContext) converter.getMappingContext();
        mappingContext.setAutoIndexCreation(true);
        
        return template;
    }

    /**
     * 配置 MongoDB 轉換器
     */
    @Bean
    public MappingMongoConverter mappingMongoConverter() {
        DefaultDbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoTemplate().getMongoDatabaseFactory());
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, new MongoMappingContext());
        
        // 移除 _class 字段
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        
        return converter;
    }

    /**
     * 配置批次寫入的 MongoDB Template
     */
    @Bean(name = "batchMongoTemplate")
    public MongoTemplate batchMongoTemplate() {
        MongoTemplate template = new MongoTemplate(mongoClient(), getDatabaseName());
        
        // 配置批次寫入優化
        MappingMongoConverter converter = (MappingMongoConverter) template.getConverter();
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        
        return template;
    }
}