package com.example.migration.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

/**
 * 資料庫配置類
 * 配置 Oracle 主資料庫和批次作業資料庫連接
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.migration.repository.oracle",
    entityManagerFactoryRef = "oracleEntityManagerFactory",
    transactionManagerRef = "oracleTransactionManager"
)
public class DatabaseConfig {

    /**
     * Oracle 主資料庫配置
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.oracle")
    public HikariConfig oracleHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("Oracle-Pool");
        config.setDriverClassName("oracle.jdbc.OracleDriver");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        // Oracle 特定設定
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        return config;
    }

    /**
     * Oracle 主資料庫連接池
     */
    @Bean(name = "oracleDataSource")
    @Primary
    public DataSource oracleDataSource(@Qualifier("oracleHikariConfig") HikariConfig config) {
        return new HikariDataSource(config);
    }

    /**
     * 批次作業專用資料庫配置
     */
    @Bean
    @ConfigurationProperties("spring.datasource.batch")
    public HikariConfig batchHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("Batch-Pool");
        config.setDriverClassName("oracle.jdbc.OracleDriver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1800000);
        
        return config;
    }

    /**
     * 批次作業資料庫連接池
     */
    @Bean(name = "batchDataSource")
    public DataSource batchDataSource(@Qualifier("batchHikariConfig") HikariConfig config) {
        return new HikariDataSource(config);
    }

    /**
     * Oracle JPA 設定
     */
    private Properties oracleJpaProperties() {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", "org.hibernate.dialect.Oracle12cDialect");
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.show_sql", "false");
        properties.put("hibernate.format_sql", "true");
        properties.put("hibernate.jdbc.batch_size", "50");
        properties.put("hibernate.order_inserts", "true");
        properties.put("hibernate.order_updates", "true");
        properties.put("hibernate.jdbc.batch_versioned_data", "true");
        properties.put("hibernate.cache.use_second_level_cache", "false");
        properties.put("hibernate.cache.use_query_cache", "false");
        properties.put("hibernate.connection.provider_disables_autocommit", "true");
        properties.put("hibernate.jdbc.lob.non_contextual_creation", "true");
        
        return properties;
    }

    /**
     * Oracle Entity Manager Factory
     */
    @Bean(name = "oracleEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean oracleEntityManagerFactory(
            @Qualifier("oracleDataSource") DataSource dataSource) {
        
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.example.migration.model.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaProperties(oracleJpaProperties());
        factory.setPersistenceUnitName("oracle");
        
        return factory;
    }

    /**
     * Oracle Transaction Manager
     */
    @Bean(name = "oracleTransactionManager")
    @Primary
    public PlatformTransactionManager oracleTransactionManager(
            @Qualifier("oracleEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        transactionManager.setNestedTransactionAllowed(true);
        
        return transactionManager;
    }
}