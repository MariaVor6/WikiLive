package com.wikilive.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun initDatabase(config: ApplicationConfig) {
        val dataSource = HikariDataSource(HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = config.property("db.url").getString()
            username = config.property("db.user").getString()
            password = config.property("db.password").getString()
            maximumPoolSize = 10
        })
        TransactionManager.defaultDatabase = Database.connect(dataSource)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Pages, PageVersions, Comments)
        }
    }
}
