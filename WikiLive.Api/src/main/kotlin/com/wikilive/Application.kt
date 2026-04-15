package com.wikilive

import com.wikilive.db.DatabaseFactory
import com.wikilive.routes.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.config.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.slf4j.event.Level
import com.typesafe.config.ConfigFactory

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 5132) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    testableModule(HoconApplicationConfig(ConfigFactory.load()))
}

fun Application.testableModule(
    dbConfig: io.ktor.server.config.ApplicationConfig? = null,
    mwsClient: io.ktor.client.HttpClient? = null
) {
    DatabaseFactory.initDatabase(dbConfig ?: environment.config)

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            prettyPrint = false
            isLenient = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
        }
    }

    routing {
        swaggerUI(path = "/", swaggerFile = "openapi/documentation.yaml")

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/api") {
            pagesRoutes()
            commentsRoutes()
            mwsProxyRoutes(mwsClient ?: io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json()
                }
                expectSuccess = false
            })
        }
    }
}
