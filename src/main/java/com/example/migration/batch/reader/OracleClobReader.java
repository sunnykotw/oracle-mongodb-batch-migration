package com.example.migration.batch.reader;

import com.example.migration.model.entity.OracleEntity;
import com.example.migration.service.ConfigurationService;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Oracle CLOB 資料讀取器
 * 從 Oracle 資料庫讀取 CLOB 資料
 */
@Component
public class OracleClobReader implements ItemReader<OracleEntity> {

    @Autowired
    @Qualifier("oracleDataSource")
    private DataSource dataSource;

    @Autowired
    private ConfigurationService configurationService;

    private JdbcCursorItemReader<OracleEntity> delegate;
    private boolean initialized = false;

    @Override
    public OracleEntity read() throws Exception {
        if (!initialized) {
            initialize();
        }
        return delegate.read();
    }

    private void initialize() {
        String sql = buildSql();
        
        delegate = new JdbcCursorItemReaderBuilder<OracleEntity>()
                .name("oracleClobReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(new OracleEntityRowMapper())
                .fetchSize(1000)
                .build();
        
        delegate.afterPropertiesSet();
        initialized = true;
    }

    private String buildSql() {
        var config = configurationService.getCurrentJobConfig();
        var sourceConfig = config.getSource().getOracle();
        
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // 添加 key columns
        List<String> keyColumns = sourceConfig.getKeyColumns();
        keyColumns.forEach(col -> sql.append(col).append(", "));
        
        // 添加 CLOB columns
        List<String> clobColumns = sourceConfig.getClobColumns();
        clobColumns.forEach(col -> sql.append(col).append(", "));
        
        // 移除最後的逗號
        sql.setLength(sql.length() - 2);
        
        sql.append(" FROM ").append(sourceConfig.getOwner())
           .append(".").append(sourceConfig.getTable());
        
        // 添加 WHERE 條件
        if (sourceConfig.getWhereCondition() != null && !sourceConfig.getWhereCondition().isEmpty()) {
            sql.append(" WHERE ").append(sourceConfig.getWhereCondition());
        }
        
        sql.append(" ORDER BY ").append(keyColumns.get(0));
        
        return sql.toString();
    }

    /**
     * Oracle Entity Row Mapper
     */
    private static class OracleEntityRowMapper implements RowMapper<OracleEntity> {
        @Override
        public OracleEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            OracleEntity entity = new OracleEntity();
            
            // 動態映射所有列
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                Object value = rs.getObject(i);
                
                // 處理 CLOB 類型
                if (value instanceof java.sql.Clob) {
                    java.sql.Clob clob = (java.sql.Clob) value;
                    value = clob.getSubString(1, (int) clob.length());
                }
                
                entity.addField(columnName, value);
            }
            
            return entity;
        }
    }
}