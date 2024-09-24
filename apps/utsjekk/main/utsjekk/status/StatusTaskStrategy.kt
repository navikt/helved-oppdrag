package utsjekk.status

import com.fasterxml.jackson.module.kotlin.readValue
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import utsjekk.clients.OppdragClient
import utsjekk.iverksetting.*
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.task.*

class StatusTaskStrategy(
    private val oppdragClient: OppdragClient,
) : TaskStrategy {
    override suspend fun isApplicable(task: TaskDao): Boolean = task.kind == Kind.SjekkStatus

    override suspend fun execute(task: TaskDao) {
        val oppdragIdDto = objectMapper.readValue<OppdragIdDto>(task.payload)
        val status = oppdragClient.hentStatus(oppdragIdDto)

        when (status.status) {
            OppdragStatus.KVITTERT_OK -> {
                IverksettingResultater.oppdater(oppdragIdDto.tilUtbetalingId(), OppdragResultat(status.status))
                Tasks.update(task.id, Status.COMPLETE, "")
            }

            OppdragStatus.KVITTERT_MED_MANGLER, OppdragStatus.KVITTERT_TEKNISK_FEIL, OppdragStatus.KVITTERT_FUNKSJONELL_FEIL -> {
                IverksettingResultater.oppdater(oppdragIdDto.tilUtbetalingId(), OppdragResultat(status.status))
                appLog.error("Mottok feilkvittering ${status.status} fra OS for oppdrag $oppdragIdDto")
                secureLog.error(
                    "Mottok feilkvittering ${status.status} fra OS for oppdrag $oppdragIdDto. Feilmelding: ${status.feilmelding}",
                )
                Tasks.update(task.id, Status.MANUAL, status.feilmelding)
            }

            OppdragStatus.KVITTERT_UKJENT -> {
                IverksettingResultater.oppdater(oppdragIdDto.tilUtbetalingId(), OppdragResultat(status.status))
                appLog.error("Mottok ukjent kvittering fra OS for oppdrag $oppdragIdDto")
                Tasks.update(task.id, Status.MANUAL, "Ukjent kvittering fra OS")
            }

            OppdragStatus.LAGT_PÅ_KØ -> {
                Tasks.update(task.id, task.status, null)
            }

            OppdragStatus.OK_UTEN_UTBETALING -> {
                error("Status ${status.status} skal aldri mottas fra utsjekk-oppdrag.")
            }
        }
    }

    companion object {
        fun metadataStrategy(payload: String): Map<String, String> {
            val oppdragIdDto = objectMapper.readValue<OppdragIdDto>(payload)
            return mapOf(
                "sakId" to oppdragIdDto.sakId,
                "behandlingId" to oppdragIdDto.behandlingId,
                "iverksettingId" to oppdragIdDto.iverksettingId.toString(),
                "fagsystem" to oppdragIdDto.fagsystem.name,
            )
        }
    }
}

fun OppdragIdDto.tilUtbetalingId() =
    UtbetalingId(
        fagsystem = this.fagsystem,
        sakId = SakId(this.sakId),
        behandlingId = BehandlingId(this.behandlingId),
        iverksettingId = this.iverksettingId?.let { IverksettingId(it) },
    )