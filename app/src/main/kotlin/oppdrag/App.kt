package oppdrag

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import libs.utils.appLog
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import oppdrag.iverksetting.OppdragService
import oppdrag.iverksetting.iverksettingRoute
import oppdrag.iverksetting.mq.OppdragMQConsumer
import oppdrag.iverksetting.mq.OppdragMQFactory
import oppdrag.postgres.Postgres

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> appLog.error("Uhåndtert feil", e) }
    embeddedServer(Netty, port = 8080, module = Application::oppdrag).start(wait = true)
}

fun Application.oppdrag(config: Config = Config()) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            JavaTimeModule()
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    val datasource = Postgres.createAndMigrate(config.postgres)
    val mqFactory = OppdragMQFactory.default(config.oppdrag)

    val oppdragConsumer = OppdragMQConsumer(
        config = config.oppdrag,
        postgres = datasource,
        factory = mqFactory,
    )

    val oppdragService = OppdragService(
        config = config.oppdrag,
        postgres = datasource,
        mqFactory = mqFactory,
    )

    environment.monitor.subscribe(ApplicationStopping) {
        oppdragConsumer.close()
    }

    environment.monitor.subscribe(ApplicationStarted) {
        oppdragConsumer.start()
    }

    routing {
        iverksettingRoute(oppdragService, datasource)
        openAPI(path="api", swaggerFile = "openapi.yml")

        actuators(prometheus)
    }
}

private fun Routing.actuators(prometheus: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metrics") {
            call.respond(prometheus.scrape())
        }
        get("/live") {
            call.respond(HttpStatusCode.OK, "live")
        }
        get("/ready") {
            call.respond(HttpStatusCode.OK, "ready")
        }
    }
}
