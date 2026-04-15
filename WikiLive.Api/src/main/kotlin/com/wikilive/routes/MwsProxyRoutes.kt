package com.wikilive.routes

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonElement

fun Route.mwsProxyRoutes(client: HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
    expectSuccess = false
}) {
    val baseUrl = "https://fusion.mws.ru"

    fun RoutingContext.getAuthHeader(): String {
        return call.request.headers["Authorization"] ?: "Bearer usk-test-token"
    }

    route("/mws-proxy") {
        get("/spaces") {
            val auth = getAuthHeader()
            val response = client.get("$baseUrl/fusion/v1/spaces") {
                headers { append(HttpHeaders.Authorization, auth) }
            }
            call.respondText(response.bodyAsText(), status = response.status)
        }

        get("/spaces/{spaceId}/nodes") {
            val spaceId = call.parameters["spaceId"]!!
            val type = call.request.queryParameters["type"]
            val auth = getAuthHeader()
            val url = buildString {
                append("$baseUrl/fusion/v1/spaces/$spaceId/nodes")
                if (type != null) append("?type=$type")
            }
            val response = client.get(url) {
                headers { append(HttpHeaders.Authorization, auth) }
            }
            call.respondText(response.bodyAsText(), status = response.status)
        }

        get("/datasheets/{dstId}/fields") {
            val dstId = call.parameters["dstId"]!!
            val auth = getAuthHeader()
            val response = client.get("$baseUrl/fusion/v1/datasheets/$dstId/fields") {
                headers { append(HttpHeaders.Authorization, auth) }
            }
            call.respondText(response.bodyAsText(), status = response.status)
        }

        get("/datasheets/{dstId}/records") {
            val dstId = call.parameters["dstId"]!!
            val auth = getAuthHeader()
            val response = client.get("$baseUrl/fusion/v1/datasheets/$dstId/records") {
                headers { append(HttpHeaders.Authorization, auth) }
            }
            call.respondText(response.bodyAsText(), status = response.status)
        }

        patch("/datasheets/{dstId}/records") {
            val dstId = call.parameters["dstId"]!!
            val auth = getAuthHeader()
            val body = call.receive<JsonElement>()
            val response = client.patch("$baseUrl/fusion/v1/datasheets/$dstId/records") {
                contentType(ContentType.Application.Json)
                headers { append(HttpHeaders.Authorization, auth) }
                setBody(body)
            }
            call.respondText(response.bodyAsText(), status = response.status)
        }
    }
}
