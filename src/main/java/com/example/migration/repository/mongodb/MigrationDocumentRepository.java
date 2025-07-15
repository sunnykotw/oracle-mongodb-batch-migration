package com.example.migration.repository.mongodb;

import com.example.migration.model.document.MigrationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MigrationDocumentRepository extends MongoRepository<MigrationDocument, String> {
    // 可根據需求擴充查詢方法
}