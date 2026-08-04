package com.virtixstudio.kruxai.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MemoryDao {

    @Insert
    void insertFact(MemoryEntity memory);

    @Query("SELECT * FROM ai_memory ORDER BY timestamp DESC")
    List<MemoryEntity> getAllMemories();

    @Query("DELETE FROM ai_memory WHERE id = :id")
    void deleteMemory(int id);
}
