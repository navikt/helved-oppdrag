package oppdrag.fakes

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.auth.AzureConfig
import libs.auth.TEST_JWKS
import oppdrag.port
import java.net.URI

class AzureFake : AutoCloseable {
    private val azure = embeddedServer(Netty, port = 0, module = Application::azure).apply { start() }

    val config: AzureConfig
        get() = AzureConfig(
            tokenEndpoint = "http://localhost:${azure.port()}/token".let(::URI).toURL(),
            jwks = "http://localhost:${azure.port()}/jwks".let(::URI).toURL(),
            issuer = "test",
            clientId = "hei",
            clientSecret = "på deg"
        )

    override fun close() {
        azure.stop(0, 0)
    }
}

private fun Application.azure() {
    install(ContentNegotiation) { jackson() }
    routing {
        get("/jwks") {
            call.respondText(TEST_JWKS)
        }
    }
}
