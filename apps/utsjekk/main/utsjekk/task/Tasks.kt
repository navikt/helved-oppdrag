package utsjekk.task

import libs.postgres.concurrency.transaction
import java.time.LocalDateTime
import java.util.*

object Tasks {

    suspend fun incomplete(): List<TaskDto> =
        transaction {
            TaskDao.select(status = Status.entries - Status.COMPLETE)
                .map(TaskDto::from)
        }

    suspend fun forKind(kind: Kind): List<TaskDto> =
        transaction {
            TaskDao.select(kind = kind)
                .map(TaskDto::from)
        }

    suspend fun forStatus(status: Status): List<TaskDto> =
        transaction {
            TaskDao.select(status = listOf(status))
                .map(TaskDto::from)
        }

    suspend fun createdAfter(after: LocalDateTime): List<TaskDto> =
        transaction {
            TaskDao.select(createdAt = SelectTime(Operator.GE, after))
                .map(TaskDto::from)
        }

    suspend fun update(id: UUID, status: Status, msg: String?) =
        transaction {
            val task = TaskDao.select(id = id).single()
            task.copy(
                status = status,
                updatedAt = LocalDateTime.now(),
                attempt = task.attempt + 1,
                message = msg
            ).update()
        }
}
