package com.example.migration.batch.writer;

import com.example.migration.model.document.MigrationDocument;
import com.example.migration.service.ConfigurationService;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MongoDB 文檔寫入器
 * 將遷移文檔寫入 MongoDB
 */
@Component
public class MongoDocumentWriter implements ItemWriter<MigrationDocument> {

    @Autowired
    @Qualifier("batchMongoTemplate")
    private MongoTemplate mongoTemplate;

    @Autowired
    private ConfigurationService configurationService;

    @Override
    public void write(Chunk<? extends MigrationDocument> chunk) throws Exception {
        List<? extends MigrationDocument> documents = chunk.getItems();
        
        String collectionName = getCollectionName();
        
        for (MigrationDocument document : documents) {
            // 使用 upsert 操作，避免重複插入
            Query query = new Query(Criteria.where("id").is(document.getId()));
            
            Update update = new Update()
                    .set("sourceTable", document.getSourceTable())
                    .set("migrationTime", document.getMigrationTime())
                    .set("version", document.getVersion())
                    .set("data", document.getData());
            
            mongoTemplate.upsert(query, update, collectionName);
        }
    }

    private String getCollectionName() {
        var config = configurationService.getCurrentJobConfig();
        return config.getTarget().getMongodb().getCollection();
    }
}