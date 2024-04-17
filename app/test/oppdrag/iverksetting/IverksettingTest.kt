package oppdrag.iverksetting

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.withTimeout
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import oppdrag.TestEnvironment
import oppdrag.etUtbetalingsoppdrag
import oppdrag.httpClient
import oppdrag.iverksetting.tilstand.OppdragId
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.server
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IverksettingTest {

    @Test
    fun `skal lagre oppdrag for utbetalingoppdrag`() {
        val utbetalingsoppdrag = etUtbetalingsoppdrag()
        var oppdragStatus: OppdragStatus = OppdragStatus.LAGT_PÅ_KØ

        testApplication {
            application { server(TestEnvironment.config) }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestEnvironment.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }

            withTimeout(1_000) {
                TestEnvironment.postgres.transaction { con ->
                    while (oppdragStatus == OppdragStatus.LAGT_PÅ_KØ) {
                        oppdragStatus = OppdragLagerRepository.hentOppdrag(utbetalingsoppdrag.oppdragId, con).status
                    }
                }
            }
            assertEquals(OppdragStatus.KVITTERT_OK, oppdragStatus)
        }
    }

    @Test
    fun `skal returnere https statuscode 409 ved dobbel sending`() {
        val utbetalingsoppdrag = etUtbetalingsoppdrag()
        var oppdragStatus: OppdragStatus = OppdragStatus.LAGT_PÅ_KØ

        testApplication {
            application { server(TestEnvironment.config) }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestEnvironment.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestEnvironment.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Conflict, it.status)
            }

            withTimeout(1_000) {
                TestEnvironment.postgres.transaction { con ->
                    while (oppdragStatus == OppdragStatus.LAGT_PÅ_KØ) {
                        oppdragStatus = OppdragLagerRepository.hentOppdrag(utbetalingsoppdrag.oppdragId, con).status
                    }
                }
            }

            assertEquals(OppdragStatus.KVITTERT_OK, oppdragStatus)
        }
    }
}

private val Utbetalingsoppdrag.oppdragId
    get() =
        OppdragId(
            fagsystem = this.fagsystem,
            fagsakId = this.saksnummer,
            behandlingId = this.utbetalingsperiode[0].behandlingId,
            iverksettingId = this.iverksettingId,
        )
