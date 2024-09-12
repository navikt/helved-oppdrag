package utsjekk.iverksetting.utbetalingsoppdrag.bdd

import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.felles.StønadType
import no.nav.utsjekk.kontrakter.felles.StønadTypeDagpenger
import org.junit.jupiter.api.Assertions.assertTrue
import utsjekk.iverksetting.BehandlingId
import java.time.LocalDate

data class ForventetUtbetalingsoppdrag(
    override val behandlingId: BehandlingId,
    val erFørsteUtbetalingPåSak: Boolean,
    val utbetalingsperiode: List<ForventetUtbetalingsperiode>,
) : WithBehandlingId(behandlingId) {
    companion object {
        fun from(rows: List<UtbetalingsgeneratorBddTest.Expected>): ForventetUtbetalingsoppdrag {
            assertTrue(rows.all { row -> row.behandlingId == rows.first().behandlingId })
            assertTrue(rows.all { row -> row.førsteUtbetSak == rows.first().førsteUtbetSak })

            return ForventetUtbetalingsoppdrag(
                behandlingId = rows.first().behandlingId,
                erFørsteUtbetalingPåSak = rows.first().førsteUtbetSak,
                utbetalingsperiode = rows.map(ForventetUtbetalingsperiode::from)
            )
        }
    }
}

data class ForventetUtbetalingsperiode(
    val erEndringPåEksisterendePeriode: Boolean,
    val periodeId: Long,
    val forrigePeriodeId: Long?,
    val sats: Int,
    val ytelse: StønadType,
    val fom: LocalDate,
    val tom: LocalDate,
    val opphør: LocalDate?,
    val satstype: Satstype,
) {
    companion object {
        fun from(expected: UtbetalingsgeneratorBddTest.Expected) = ForventetUtbetalingsperiode(
            erEndringPåEksisterendePeriode = expected.erEndring,
            periodeId = expected.periodeId,
            forrigePeriodeId = expected.forrigePeriodeId,
            sats = expected.beløp,
            ytelse = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR, // fixme: legg til i expected?
            fom = expected.fom,
            tom = expected.tom,
            opphør = expected.opphørsdato,
            satstype = expected.satstype,
        )
    }
}
