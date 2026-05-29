package com.example.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_apps")
data class CachedAppEntity(
    @PrimaryKey val appId: String,
    val appName: String,
    val appPackage: String,
    val appIcon: String,
    val appBanner: String,
    val category: String,
    val rating: Double,
    val downloads: String,
    val size: String,
    val developerName: String,
    val shortDescription: String,
    val fullDescription: String,
    val featured: Boolean,
    val topChart: Boolean,
    val trending: Boolean,
    val premium: Boolean
)

@Entity(tableName = "cached_categories")
data class CachedCategoryEntity(
    @PrimaryKey val categoryId: String,
    val categoryName: String,
    val categoryIcon: String
)
