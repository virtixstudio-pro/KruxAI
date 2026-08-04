package com.virtixstudio.kruxai.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ai_memory")
public class MemoryEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String key;
    public String value;
    public long timestamp;

    public MemoryEntity(String key, String value, long timestamp) {
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
    }
}
