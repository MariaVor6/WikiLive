package com.wikilive.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.StringColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.statements.jdbc.JdbcPreparedStatementImpl
import org.postgresql.util.PGobject

class JsonBColumnType : StringColumnType() {
    override fun sqlType(): String = "jsonb"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val pgObj = value?.let {
            PGobject().apply {
                type = "jsonb"
                this.value = it as String
            }
        }
        when (stmt) {
            is JdbcPreparedStatementImpl -> stmt.statement.setObject(index, pgObj)
            else -> super.setParameter(stmt, index, value)
        }
    }
}

fun Table.jsonb(name: String): Column<String> = registerColumn(name, JsonBColumnType())

object Pages : Table("pages") {
    val id = uuid("id")
    val title = varchar("title", 255)
    val content = jsonb("content").nullable()
    val spaceId = varchar("space_id", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val createdBy = varchar("created_by", 255).nullable()
    val updatedBy = varchar("updated_by", 255).nullable()
    val version = integer("version")

    override val primaryKey = PrimaryKey(id)
}

object PageVersions : Table("page_versions") {
    val id = uuid("id")
    val pageId = reference("page_id", Pages.id, onDelete = ReferenceOption.CASCADE)
    val versionNumber = integer("version_number")
    val content = jsonb("content").nullable()
    val snapshotTitle = varchar("snapshot_title", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val createdBy = varchar("created_by", 255).nullable()
    val comment = varchar("comment", 1000).nullable()

    override val primaryKey = PrimaryKey(id)
}

object Comments : Table("comments") {
    val id = uuid("id")
    val pageId = reference("page_id", Pages.id, onDelete = ReferenceOption.CASCADE)
    val selectedText = varchar("selected_text", 1000).nullable()
    val anchor = jsonb("anchor").nullable()
    val text = varchar("text", 1000)
    val resolved = bool("resolved")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val createdBy = varchar("created_by", 255).nullable()
    val parentId = uuid("parent_id").nullable()
    val likes = integer("likes")

    override val primaryKey = PrimaryKey(id)
}
