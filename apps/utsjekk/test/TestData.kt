import no.nav.utsjekk.kontrakter.felles.*
import no.nav.utsjekk.kontrakter.iverksett.*
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import utsjekk.iverksetting.*
import java.time.LocalDate
import java.time.LocalDateTime

typealias AndelPeriode = Pair<LocalDate, LocalDate>

object TestData {
    private val DEFAULT_FAGSYSTEM: Fagsystem = Fagsystem.DAGPENGER

    object dao {
        fun iverksetting(
            behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
            iverksetting: Iverksetting = domain.iverksetting(behandlingId = behandlingId),
            mottattTidspunkt: LocalDateTime = LocalDateTime.now(),
        ): IverksettingDao = IverksettingDao(
            behandlingId = behandlingId,
            data = iverksetting,
            mottattTidspunkt = mottattTidspunkt,
        )

        fun iverksettingResultat(
            fagsystem: Fagsystem = DEFAULT_FAGSYSTEM,
            sakId: SakId = SakId(RandomOSURId.generate()),
            behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
            iverksettingId: IverksettingId? = null,
            tilkjentYtelse: TilkjentYtelse? = null,
            resultat: OppdragResultat? = null,
        ): IverksettingResultatDao = IverksettingResultatDao(
            fagsystem = fagsystem,
            sakId = sakId,
            behandlingId = behandlingId,
            iverksettingId = iverksettingId,
            tilkjentYtelseForUtbetaling = tilkjentYtelse,
            oppdragResultat = resultat,
        )
    }

    object dto {
        fun iverksetting(
            behandlingId: String = RandomOSURId.generate(),
            sakId: String = RandomOSURId.generate(),
            iverksettingId: String? = null,
            personident: Personident = Personident("15507600333"),
            vedtak: VedtaksdetaljerV2Dto = vedtaksdetaljer(),
            forrigeIverksetting: ForrigeIverksettingV2Dto? = null,
        ) = IverksettV2Dto(
            behandlingId = behandlingId,
            sakId = sakId,
            iverksettingId = iverksettingId,
            personident = personident,
            vedtak = vedtak,
            forrigeIverksetting = forrigeIverksetting,
        )

        fun vedtaksdetaljer(
            vedtakstidspunkt: LocalDateTime = LocalDateTime.of(2021, 5, 12, 0, 0),
            saksbehandlerId: String = "A12345",
            beslutterId: String = "B23456",
            utbetalinger: List<UtbetalingV2Dto> = listOf(utbetaling()),
        ) = VedtaksdetaljerV2Dto(
            vedtakstidspunkt = vedtakstidspunkt,
            saksbehandlerId = saksbehandlerId,
            beslutterId = beslutterId,
            utbetalinger = utbetalinger,
        )

        fun utbetaling(
            beløp: UInt = 500u,
            satstype: Satstype = Satstype.DAGLIG,
            fom: LocalDate = LocalDate.of(2021, 1, 1),
            tom: LocalDate = LocalDate.of(2021, 12, 31),
            stønadsdata: StønadsdataDto = dagpengestønad()
        ) =
            UtbetalingV2Dto(
                beløp = beløp,
                satstype = satstype,
                fraOgMedDato = fom,
                tilOgMedDato = tom,
                stønadsdata = stønadsdata
            )

        fun dagpengestønad(
            type: StønadTypeDagpenger = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR,
            ferietillegg: Ferietillegg? = null,
        ) = StønadsdataDagpengerDto(type, ferietillegg)

        fun tilleggstønad(
            type: StønadTypeTilleggsstønader = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
            brukersNavKontor: String? = null,
        ) = StønadsdataTilleggsstønaderDto(type, brukersNavKontor)

        fun oppdragStatus(
            status: OppdragStatus = OppdragStatus.LAGT_PÅ_KØ,
            feilmelding: String? = null,
        ): OppdragStatusDto = OppdragStatusDto(
            status = status,
            feilmelding = feilmelding,
        )

        fun oppdragId(iverksetting: Iverksetting) = OppdragIdDto(
            fagsystem = iverksetting.fagsak.fagsystem,
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
        )
    }

    object domain {

        fun tidligereIverksetting(
            andelsdatoer: List<AndelPeriode> = emptyList()
        ): Iverksetting {
            val iverksetting = iverksetting(andelsdatoer = andelsdatoer)
            val andelerTilkjentYtelse =
                iverksetting.vedtak.tilkjentYtelse.andelerTilkjentYtelse.mapIndexed { idx, andel ->
                    andel.copy(
                        periodeId = idx.toLong(),
                        forrigePeriodeId = if (idx > 0) idx - 1L else null
                    )
                }

            val sisteAndel = andelerTilkjentYtelse.maxBy {  andel -> andel.periodeId!! }
            val sisteAndelPerKjede = mapOf(sisteAndel.stønadsdata.tilKjedenøkkel() to sisteAndel)

            val tilkjentYtelse = iverksetting.vedtak.tilkjentYtelse.copy(
                andelerTilkjentYtelse = andelerTilkjentYtelse,
                sisteAndelPerKjede = sisteAndelPerKjede,
            )
            val vedtak = iverksetting.vedtak.copy(tilkjentYtelse = tilkjentYtelse)
            return iverksetting.copy(vedtak = vedtak)
        }

        fun iverksetting(
            fagsystem: Fagsystem = DEFAULT_FAGSYSTEM,
            sakId: SakId = SakId(RandomOSURId.generate()),
            behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
            forrigeBehandlingId: BehandlingId? = null,
            iverksettingId: IverksettingId? = null,
            forrigeIverksettingId: IverksettingId? = null,
            andelsdatoer: List<AndelPeriode> = emptyList(),
            beløp: Int = 100,
            vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        ): Iverksetting {

            return Iverksetting(
                fagsak = Fagsakdetaljer(sakId, fagsystem),
                behandling = Behandlingsdetaljer(
                    behandlingId = behandlingId,
                    iverksettingId = iverksettingId,
                    forrigeBehandlingId = forrigeBehandlingId,
                    forrigeIverksettingId = forrigeIverksettingId,
                ),
                søker = Søker(
                    personident = "15507600333"
                ),
                vedtak = vedtaksdetaljer(
                    andeler = andelsdatoer.map { (fom, tom) ->
                        enAndelTilkjentYtelse(
                            beløp = beløp,
                            fom = fom,
                            tom = tom,
                        )
                    },
                    vedtakstidspunkt = vedtakstidspunkt
                )
            )
        }

        fun vedtaksdetaljer(
            andeler: List<AndelTilkjentYtelse> = listOf(enAndelTilkjentYtelse()),
            vedtakstidspunkt: LocalDateTime = LocalDateTime.of(2021, 5, 12, 0, 0),
        ): Vedtaksdetaljer = Vedtaksdetaljer(
            vedtakstidspunkt = vedtakstidspunkt,
            saksbehandlerId = "A12345",
            beslutterId = "B23456",
            tilkjentYtelse = enTilkjentYtelse(andeler),
        )

        fun enAndelTilkjentYtelse(
            beløp: Int = 5000,
            fom: LocalDate = LocalDate.of(2021, 1, 1),
            tom: LocalDate = LocalDate.of(2021, 12, 31),
            periodeId: Long? = null,
            forrigePeriodeId: Long? = null,
            stønadsdata: Stønadsdata = StønadsdataDagpenger(
                stønadstype = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR
            )
        ): AndelTilkjentYtelse = AndelTilkjentYtelse(
            beløp = beløp,
            periode = Periode(fom, tom),
            periodeId = periodeId,
            forrigePeriodeId = forrigePeriodeId,
            stønadsdata = stønadsdata,
        )

        fun enTilkjentYtelse(
            andeler: List<AndelTilkjentYtelse>
        ): TilkjentYtelse = TilkjentYtelse(
            id = RandomOSURId.generate(),
            utbetalingsoppdrag = null,
            andelerTilkjentYtelse = andeler,
        )
    }


}
