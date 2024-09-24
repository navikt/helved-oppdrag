package utsjekk.task

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import utsjekk.task.history.TaskHistory
import java.time.LocalDateTime
import java.util.UUID
import kotlin.coroutines.CoroutineContext

fun Route.tasks(context: CoroutineContext) {
    route("/api/tasks") {
        get {
            val status = call.parameters["status"]?.split(",")?.map { Status.valueOf(it) }
            val after = call.parameters["after"]?.let { LocalDateTime.parse(it) }
            val kind = call.parameters["kind"]?.let { Kind.valueOf(it) }

            val page = call.parameters["page"]?.toInt()
            val pageSize = call.parameters["pageSize"]?.toInt() ?: 20

            withContext(context) {
                if (page != null) {
                    val tasks = Tasks.filterBy(status, after, kind, pageSize, (page - 1) * pageSize)
                    val count = Tasks.count(status, after, kind)
                    call.respond(PaginatedTasksDto(tasks, page, pageSize, count))
                } else {
                    val tasks = Tasks.filterBy(status, after, kind)
                    call.respond(tasks)
                }
            }
        }

        patch("/{id}") {
            val id =
                call.parameters["id"]?.let(UUID::fromString)
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "mangler påkrevd path parameter 'id'")

            withContext(context) {
                transaction {
                    TaskDao.select { it.id = id }
                }
            }.singleOrNull() ?: return@patch call.respond(HttpStatusCode.NotFound, "Fant ikke task med id $id")

            val payload = call.receive<TaskDtoPatch>()

            withContext(context) {
                Tasks.update(id, payload.status, payload.message)
            }
        }

        get("/{id}/history") {
            val id =
                call.parameters["id"]?.let(UUID::fromString)
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd path parameter 'id'")

            withContext(context) {
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