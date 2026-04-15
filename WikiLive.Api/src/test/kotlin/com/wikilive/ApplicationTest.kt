package com.wikilive

import com.wikilive.models.Comment
import com.wikilive.models.CommentResolveBody
import com.wikilive.models.CommentUpdate
import com.wikilive.models.Page
import com.wikilive.models.PageActivityItem
import com.wikilive.routes.commentsRoutes
import com.wikilive.routes.mwsProxyRoutes
import com.wikilive.routes.pagesRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationTest {

    companion object {
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("wiki")
            withUsername("postgres")
            withPassword("postgres")
        }

        @JvmStatic
        @BeforeAll
        fun startContainer() {
            postgres.start()
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            postgres.stop()
        }
    }

    private fun testDbConfig(): MapApplicationConfig {
        return MapApplicationConfig().apply {
            put("db.url", postgres.jdbcUrl)
            put("db.user", postgres.username)
            put("db.password", postgres.password)
        }
    }

    @Test
    fun testGetPages() = testApplication {
        application {
            testableModule(testDbConfig())
        }
        client.get("/api/pages").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertTrue(body.startsWith("["))
        }
    }

    @Test
    fun testCreateAndGetPage() = testApplication {
        application {
            testableModule(testDbConfig())
        }
        val newPage = Page(title = "Test Page", content = JsonPrimitive("Hello"))
        val createResponse = client.post("/api/pages") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), newPage))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        val created = Json.decodeFromString<Page>(createResponse.bodyAsText())
        assertNotNull(created.id)
        assertEquals("Test Page", created.title)

        val getResponse = client.get("/api/pages/${created.id}")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetched = Json.decodeFromString<Page>(getResponse.bodyAsText())
        assertEquals(created.id, fetched.id)
        assertEquals("Test Page", fetched.title)
    }

    @Test
    fun testUpdatePageCreatesVersion() = testApplication {
        application {
            testableModule(testDbConfig())
        }
        val newPage = Page(title = "Original", content = JsonPrimitive("v1"))
        val createResponse = client.post("/api/pages") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), newPage))
        }
        val created = Json.decodeFromString<Page>(createResponse.bodyAsText())
        val id = created.id!!

        val updated = created.copy(title = "Updated", content = JsonPrimitive("v2"))
        val putResponse = client.put("/api/pages/$id?expectedVersion=${created.version}") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), updated))
        }
        assertEquals(HttpStatusCode.NoContent, putResponse.status)

        val getResponse = client.get("/api/pages/$id")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetched = Json.decodeFromString<Page>(getResponse.bodyAsText())
        assertEquals("Updated", fetched.title)
        assertEquals(2, fetched.version)
        assertEquals(1, fetched.versions.size)
        assertEquals(1, fetched.versions.first().versionNumber)
    }

    @Test
    fun testRestoreVersion() = testApplication {
        application {
            testableModule(testDbConfig())
        }
        val newPage = Page(title = "RestoreTest", content = JsonPrimitive("original content"))
        val createResponse = client.post("/api/pages") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), newPage))
        }
        val created = Json.decodeFromString<Page>(createResponse.bodyAsText())
        val id = created.id!!

        val updated = created.copy(title = "RestoreTest", content = JsonPrimitive("updated content"))
        client.put("/api/pages/$id?expectedVersion=${created.version}") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), updated))
        }

        val getResponse = client.get("/api/pages/$id")
        val withVersion = Json.decodeFromString<Page>(getResponse.bodyAsText())
        val versionId = withVersion.versions.first().id!!

        val restoreResponse = client.post("/api/pages/$id/restore/$versionId")
        assertEquals(HttpStatusCode.OK, restoreResponse.status)
        val restored = Json.decodeFromString<Page>(restoreResponse.bodyAsText())
        assertEquals(3, restored.version)
    }

    @Test
    fun testCommentLifecycle() = testApplication {
        application {
            testableModule(testDbConfig())
        }
        val newPage = Page(title = "Comment Test", content = JsonPrimitive("body"))
        val pageResponse = client.post("/api/pages") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), newPage))
        }
        val page = Json.decodeFromString<Page>(pageResponse.bodyAsText())
        val pageId = page.id!!

        val comment = Comment(pageId = pageId, text = "Nice page!")
        val createResponse = client.post("/api/comments") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Comment.serializer(), comment))
        }
        assertEquals(HttpStatusCode.OK, createResponse.status)
        val created = Json.decodeFromString<Comment>(createResponse.bodyAsText())
        assertEquals("Nice page!", created.text)
        val commentId = created.id!!

        val likeResponse = client.post("/api/comments/$commentId/like")
        assertEquals(HttpStatusCode.OK, likeResponse.status)
        val likes = Json.parseToJsonElement(likeResponse.bodyAsText()).jsonObject["likes"]?.jsonPrimitive?.int
        assertEquals(1, likes)

        val resolveResponse = client.put("/api/comments/$commentId/resolve") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CommentResolveBody.serializer(), CommentResolveBody(resolved = true)))
        }
        assertEquals(HttpStatusCode.NoContent, resolveResponse.status)

        val deleteResponse = client.delete("/api/comments/$commentId")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    @Test
    fun testUpdatePageVersionConflict() = testApplication {
        application {
            testableModule(testDbConfig())
        }
        val newPage = Page(title = "Conflict", content = JsonPrimitive("a"))
        val createResponse = client.post("/api/pages") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), newPage))
        }
        val created = Json.decodeFromString<Page>(createResponse.bodyAsText())
        val id = created.id!!

        val badPut = client.put("/api/pages/$id?expectedVersion=999") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), created.copy(title = "nope")))
        }
        assertEquals(HttpStatusCode.Conflict, badPut.status)
    }

    @Test
    fun testActivityFeed() = testApplication {
        application {
            testableModule(testDbConfig())
        }
        val newPage = Page(title = "Activity", content = JsonPrimitive("doc"))
        val pageResponse = client.post("/api/pages") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), newPage))
        }
        val page = Json.decodeFromString<Page>(pageResponse.bodyAsText())
        val pageId = page.id!!

        client.post("/api/comments") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Comment.serializer(), Comment(pageId = pageId, text = "hello")))
        }

        val activityResponse = client.get("/api/pages/$pageId/activity")
        assertEquals(HttpStatusCode.OK, activityResponse.status)
        val items = Json.decodeFromString(
            ListSerializer(PageActivityItem.serializer()),
            activityResponse.bodyAsText()
        )
        assertTrue(items.any { it.type == "comment" })
    }

    @Test
    fun testCommentPutUpdatesText() = testApplication {
        application {
            testableModule(testDbConfig())
        }
        val newPage = Page(title = "Edit comment", content = JsonPrimitive("x"))
        val pageResponse = client.post("/api/pages") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Page.serializer(), newPage))
        }
        val page = Json.decodeFromString<Page>(pageResponse.bodyAsText())
        val pageId = page.id!!

        val createResponse = client.post("/api/comments") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Comment.serializer(), Comment(pageId = pageId, text = "orig")))
        }
        val created = Json.decodeFromString<Comment>(createResponse.bodyAsText())
        val commentId = created.id!!

        val putResponse = client.put("/api/comments/$commentId") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CommentUpdate.serializer(), CommentUpdate(text = "edited")))
        }
        assertEquals(HttpStatusCode.OK, putResponse.status)
        val updated = Json.decodeFromString<Comment>(putResponse.bodyAsText())
        assertEquals("edited", updated.text)
    }

    @Test
    fun testMwsProxyWithMockEngine() = testApplication {
        val mockEngine = MockEngine { request ->
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            respond("{}", HttpStatusCode.OK, headersOf("Content-Type" to listOf("application/json")))
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
            expectSuccess = false
        }

        application {
            testableModule(testDbConfig(), mockClient)
        }

        val response = client.get("/api/mws-proxy/spaces") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{}", response.bodyAsText())
    }
}
