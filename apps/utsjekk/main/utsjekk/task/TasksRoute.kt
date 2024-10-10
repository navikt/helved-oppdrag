package utsjekk.task

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.postgres.Postgres
import libs.postgres.concurrency.transaction
import libs.task.Order
import libs.task.TaskDao
import libs.task.TaskHistory
import libs.task.Tasks
import java.time.LocalDateTime
import java.util.*

fun Route.tasks() {
    route("/api/tasks") {
        get {
            val status = call.parameters["status"]?.split(",")?.map { libs.task.Status.valueOf(it) }
            val after = call.parameters["after"]?.let { LocalDateTime.parse(it) }
            val kind = call.parameters["kind"]?.let { libs.task.Kind.valueOf(it) }

            val page = call.parameters["page"]?.toInt()
            val pageSize = call.parameters["pageSize"]?.toInt() ?: 20

            val order = Order("scheduled_for", Order.Direction.DESCENDING)

            withContext(Postgres.context) {
                if (page != null) {
                    val tasks =
                        Tasks.filterBy(
                            status = status,
                            after = after,
                            kind = kind,
                            limit = pageSize,
                            offset = (page - 1) * pageSize,
                            order = order,
                        ).map(TaskDto::from)
                    val count = Tasks.count(status, after, kind)
                    call.respond(PaginatedTasksDto(tasks, page, pageSize, count))
                } else {
                    val tasks = Tasks.filterBy(status = status, after = after, kind = kind, order = order)
                    call.respond(tasks)
                }
            }
        }

        put("/{id}/rekjør") {
            val id =
                call.parameters["id"]?.let(UUID::fromString)
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "mangler påkrevd path parameter 'id'")

            withContext(Postgres.context) {
                Tasks.rekjør(id)
            }

            call.respond(HttpStatusCode.OK)
        }

        patch("/{id}") {
            val id =
                call.parameters["id"]?.let(UUID::fromString)
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "mangler påkrevd path parameter 'id'")

            withContext(Postgres.context) {
                transaction {
                    TaskDao.select { it.id = id }
                }
            }.singleOrNull() ?: return@patch call.respond(HttpStatusCode.NotFound, "Fant ikke task med id $id")

            val payload = call.receive<TaskDtoPatch>()

            withContext(Postgres.context) {
                Tasks.update(id, libs.task.Status.valueOf(payload.status.name), payload.message) {
                    Kind.valueOf(kind.name).retryStrategy(it)
                }
                call.respond(HttpStatusCode.OK)
            }
        }

        get("/{id}/history") {
            val id =
                call.parameters["id"]?.let(UUID::fromString)
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd path parameter 'id'")

            withContext(Postgres.context) {
                val historikk = transaction { TaskHistory.history(id) }

                call.respond(historikk)
            }
        }
    }
}

data class PaginatedTasksDto(
    val tasks: List<TaskDto>,
    val page: Int,
    val pageSize: Int,
    val totalTasks: Int,
)

data class TaskDtoPatch(
    val status: Status,
    val message: String,
)
