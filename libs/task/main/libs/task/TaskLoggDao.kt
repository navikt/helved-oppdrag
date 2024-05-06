package libs.task

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.env
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

private const val BRUKERNAVN_NÅR_SIKKERHETSKONTEKST_IKKE_FINNES = "VL"

data class TaskLoggDao(
    val task_id: UUID,
    val type: Loggtype,
    val id: UUID = UUID.randomUUID(),
    val endret_av: String = BRUKERNAVN_NÅR_SIKKERHETSKONTEKST_IKKE_FINNES,
    val node: String = env("HOSTNAME", "node1"),
    val melding: String? = null,
    val opprettet_tid: LocalDateTime = LocalDateTime.now(),
) {
    suspend fun insert() {
        coroutineContext.connection.prepareStatement(
            """
           INSERT INTO task_logg (id, task_id, type, node, opprettet_tid, endret_av, melding) 
           VALUES (?,?,?,?,?,?,?)
            """
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setObject(2, task_id)
            stmt.setString(3, type.name)
            stmt.setObject(4, node)
            stmt.setTimestamp(5, Timestamp.valueOf(opprettet_tid))
            stmt.setString(6, endret_av)
            stmt.setString(7, melding)
            stmt.executeUpdate()
        }
    }

    companion object {
        suspend fun findBy(task_id: UUID): List<TaskLoggDao> = findBy(listOf(task_id))

        suspend fun findBy(task_ids: List<UUID>): List<TaskLoggDao> =
            coroutineContext.connection.prepareStatement(
                """
                    SELECT * FROM task_logg
                    WHERE task_id in (?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, task_ids.joinToString(", ") { it.toString() })
                stmt.executeQuery().map(::from)
            }

        suspend fun findMetadataBy(task_ids: List<UUID>): List<TaskLoggMetadata> =
            coroutineContext.connection.prepareStatement(
                """
                    SELECT task_id, count(*) antall_logger, MAX(opprettet_tid) siste_opprettet_tid,
                        (
                            SELECT melding FROM task_logg tl1
                            WHERE tl1.task_id = tl.task_id AND type='KOMMENTAR' ORDER BY tl1.opprettet_tid DESC LIMIT 1) 
                            siste_kommentar
                        )
                    FROM task_logg tl
                    WHERE task_id IN (?)
                    GROUP BY task_id
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, task_ids.joinToString(", ") { it.toString() })
                stmt.executeQuery().map(TaskLoggMetadata::from)
            }

        suspend fun countBy(task_id: UUID, type: Loggtype): Long =
            coroutineContext.connection.prepareStatement(
                """
                    SELECT count(*) FROM task_logg
                    WHERE task_id = ? AND type = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, task_id)
                stmt.setString(2, type.name)
                stmt.executeQuery().map { it.getLong(1) }.single()
            }

        suspend fun deleteBy(task_id: UUID) = deleteBy(listOf(task_id))

        suspend fun deleteBy(task_ids: List<UUID>): Int =
            coroutineContext.connection.prepareStatement(
                """
                    DELETE FROM task_logg
                    WHERE task_id in (?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, task_ids.joinToString(", ") { it.toString() })
                stmt.executeUpdate()
            }
    }
}

fun TaskLoggDao.Companion.from(rs: ResultSet) = TaskLoggDao(
    task_id = UUID.fromString(rs.getString("task_id")),
    type = Loggtype.valueOf(rs.getString("type")),
    id = UUID.fromString(rs.getString("id")),
    endret_av = rs.getString("endret_av"),
    node = rs.getString("node"),
    melding = rs.getString("melding"),
    opprettet_tid = rs.getTimestamp("opprettet_tid").toLocalDateTime()
)

class TaskLoggMetadata(
    val task_id: UUID,
    val antall_logger: Int,
    val siste_opprettet_tid: LocalDateTime,
    val siste_kommentar: String?,
) {
    companion object {
        fun from(rs: ResultSet) = TaskLoggMetadata(
            task_id = UUID.fromString(rs.getString("task_id")),
            antall_logger = rs.getInt("antall_logger"),
            siste_opprettet_tid = rs.getTimestamp("siste_opprettet_tid").toLocalDateTime(),
            siste_kommentar = rs.getString("siste_kommentar")
        )
    }
}
