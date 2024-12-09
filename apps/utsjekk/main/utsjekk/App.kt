package utsjekk

// imports
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.TokenProvider
import libs.auth.configure
import libs.kafka.vanilla.Kafka
import libs.kafka.vanilla.KafkaConfig
import libs.postgres.Jdbc
import libs.postgres.JdbcConfig
import libs.postgres.Migrator
import libs.utils.logger
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import utsjekk.avstemming.AvstemmingTaskStrategy
import utsjekk.clients.OppdragClient
import utsjekk.clients.SimuleringClient
import utsjekk.iverksetting.IverksettingTaskStrategy
import utsjekk.iverksetting.Iverksettinger
import utsjekk.iverksetting.iverksetting
import utsjekk.simulering.SimuleringValidator
import utsjekk.simulering.simulering
import utsjekk.status.StatusKafkaProducer
import utsjekk.status.StatusTaskStrategy
import utsjekk.task.TaskScheduler
import utsjekk.task.tasks
import utsjekk.utbetaling.* 

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080) {
        val config = Config()
        val metrics = telemetry()
        database(config.jdbc)
        val statusProducer = kafka(config.kafka)
        val unleash = featureToggles(config.unleash)
        val iverksettinger = iverksetting(unleash, statusProducer)
        scheduler(config, iverksettinger, metrics)
        routes(config, iverksettinger, metrics)
    }.start(wait = true)
}

fun Application.telemetry(
    metrics: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
): PrometheusMeterRegistry {
    install(MicrometerMetrics) {
        registry = metrics
        meterBinders += LogbackMetrics()
    }
    appLog.info("setup telemetry")
    return metrics
}

fun Application.database(config: JdbcConfig) {
    Jdbc.initialize(config)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(File("migrations")).migrate()
        }
    }
    appLog.info("setup database")
}

fun Application.kafka(
    config: KafkaConfig,
    statusProducer: Kafka<StatusEndretMelding> = StatusKafkaProducer(config),
): Kafka<StatusEndretMelding> {
    monitor.subscribe(ApplicationStopping) {
        statusProducer.close()
    }
    appLog.info("setup kafka")
    return statusProducer
}

fun Application.featureToggles(
    config: UnleashConfig,
    featureToggles: FeatureToggles = UnleashFeatureToggles(config),
): FeatureToggles {
    appLog.info("setup featureToggles")
    return featureToggles
}

fun Application.iverksetting(
    featureToggles: FeatureToggles,
    statusProducer: Kafka<StatusEndretMelding>, 
): Iverksettinger {
    appLog.info("setup iverksettinger")
    return Iverksettinger(featureToggles, statusProducer)
}

fun Application.scheduler(
    config: Config,
    iverksettinger: Iverksettinger,
    metrics: MeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
) {
    val oppdrag = OppdragClient(config)
    val scheduler = TaskScheduler(
        listOf(
            IverksettingTaskStrategy(oppdrag, iverksettinger),
            StatusTaskStrategy(oppdrag, iverksettinger),
            AvstemmingTaskStrategy(oppdrag).apply {
                runBlocking {
                    withContext(Jdbc.context) {
                        initiserAvstemmingForNyeFagsystemer()
                    }
                }
            },
            UtbetalingTaskStrategy(oppdrag),
            UtbetalingStatusTaskStrategy(oppdrag),
        ),
        LeaderElector(config),
        metrics
    )

    monitor.subscribe(ApplicationStopping) {
        scheduler.close()
    }
    appLog.info("setup scheduler")
}

fun Application.routes(
    config: Config,
    iverksettinger: Iverksettinger,
    metrics: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
) {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
    }

    install(DoubleReceive)
    install(CallLog) {
        exclude { call -> call.request.path().startsWith("/probes") }
        log { call ->
            appLog.info("${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()} in ${call.processingTimeMs()}ms")
            secureLog.info(
                """
                ${call.request.httpMethod.value} ${call.request.local.uri} gave ${call.response.status()} in ${call.processingTimeMs()}ms
                ${call.bodyAsText()}
                """.trimIndent()
            )
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause -> 
            when (cause) {
                is ApiError -> call.respond(HttpStatusCode.fromValue(cause.statusCode), cause.asResponse) 
                else -> {
                    secureLog.error("Unknown error.", cause)
                    call.respond(HttpStatusCode.InternalServerError, "Unknown error")
                }
            }
        }
    }

    val simulering = SimuleringClient(config)
    val simuleringValidator = SimuleringValidator(iverksettinger)

    routing {
        authenticate(TokenProvider.AZURE) {
            iverksetting(iverksettinger)
            simulering(simuleringValidator, simulering)
            utbetalingRoute()
            tasks()
        }

        probes(metrics)
    }

    appLog.info("setup routes")
}

fun ApplicationCall.navident(): String =
    principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
        ?: forbidden("missing JWT claim", "NAVident", "https://navikt.github.io/utsjekk-docs/kom_i_gang")

fun ApplicationCall.client(): Client =
    principal<JWTPrincipal>()
        ?.getClaim("azp_name", String::class)
        ?.split(":")
        ?.last()
        ?.let(::Client)
        ?: forbidden("missing JWT claim", "azp_name", "https://navikt.github.io/utsjekk-docs/kom_i_gang")

@JvmInline
value class Client(
    private val name: String,
) {
    fun toFagsystem(): Fagsystem =
        when (name) {
            "helved-performance" -> Fagsystem.DAGPENGER
            "tiltakspenger-saksbehandling-api" -> Fagsystem.TILTAKSPENGER
            "tilleggsstonader-sak", "azure-token-generator" -> Fagsystem.TILLEGGSSTØNADER
            else -> forbidden(
                msg = "mangler mapping mellom appname ($name) og fagsystem-enum",
                doc = "https://navikt.github.io/utsjekk-docs/kom_i_gang",
            )
        }
}
