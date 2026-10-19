package com.companionstudio.nyx.tracking

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.companionstudio.nyx.config.Category

/**
 * A category the user chose for an app. Apps without a row use the suggested category (D-58).
 * Room stores [category] by its Kotlin name, so renaming a [Category] constant needs a migration.
 */
@Entity(tableName = "app_categories")
data class AppCategoryEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    val category: Category,
)
