package com.example.migration.model.entity;

import jakarta.persistence.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Oracle 實體類
 * 動態映射 Oracle 資料
 */
@Entity
@Table(name = "DYNAMIC_ENTITY")
public class OracleEntity {

    @ElementCollection
    @MapKeyColumn(name = "FIELD_NAME")
    @Column(name = "FIELD_VALUE")
    @CollectionTable(name = "ENTITY_FIELDS")
    private Map<String, Object> fields = new HashMap<>();

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }

    public void addField(String name, Object value) {
        this.fields.put(name, value);
    }

    public Object getField(String name) {
        return this.fields.get(name);
    }

    public boolean hasField(String name) {
        return this.fields.containsKey(name);
    }

    @Override
    public String toString() {
        return "OracleEntity{" +
                "fields=" + fields +
                '}';
    }
}