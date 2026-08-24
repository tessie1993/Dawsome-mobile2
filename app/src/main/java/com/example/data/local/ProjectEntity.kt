package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daw_projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val genre: String,
    val bpm: Float,
    val keyRoot: Int = 0,
    val scaleName: String = "Minor (Aeolian)",
    val lastModified: Long = System.currentTimeMillis(),
    val projectDataJson: String
)
