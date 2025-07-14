package com.example.migration.batch.writer;

import com.example.migration.model.document.MigrationDocument;
import com.example.migration.service.ConfigurationService;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

/**
 * Oracle 封存寫入器
 * 將遷移記錄寫入 Oracle 封存表
 */
@Component
public class OracleArchiveWriter implements ItemWriter<MigrationDocument> {

    @Autowired
    @Qualifier("batchJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConfigurationService configurationService;

    @Override
    public void write(Chunk<? extends MigrationDocument> chunk) throws Exception {
        List<? extends MigrationDocument> documents = chunk.getItems();
        
        var config = configurationService.getCurrentJobConfig();
        if (!config.getArchive().isEnabled()) {
            return;
        }
        
        String archiveTable = config.getArchive().getTargetTable();
        String insertSql = buildInsertSql(archiveTable);
        
        for (MigrationDocument document : documents) {
            jdbcTemplate.update(insertSql,
                    document.getId(),
                    document.getSourceTable(),
                    new Timestamp(System.currentTimeMillis()),
                    "COMPLETED",
                    document.getData().toString()
            );
        }
    }

    private String buildInsertSql(String archiveTable) {
        return "INSERT INTO " + archiveTable + 
               " (DOCUMENT_ID, SOURCE_TABLE, MIGRATION_TIME, STATUS, DATA) " +
               "VALUES (?, ?, ?, ?, ?)";
    }
}
