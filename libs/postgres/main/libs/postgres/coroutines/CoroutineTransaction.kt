package libs.postgres.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.sql.Connection
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class CoroutineTransaction : AbstractCoroutineContextElement(CoroutineTransaction) {
    companion object Key : CoroutineContext.Key<CoroutineTransaction>

    var completed: Boolean = false
        private set

    fun complete() {
        completed = true
    }

    override fun toString(): String = "CoroutineTransaction(completed=$completed)"
}

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> transaction(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val existingTransaction = coroutineContext[CoroutineTransaction]

    return when {
        existingTransaction == null -> {
            withConnection {
                runTransactionally {
                    block()
                }
            }
        }

        !existingTransaction.completed -> {
            withContext(coroutineContext) {
                block()
            }
        }

        else -> error("Nested transactions not supported")
    }
}

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> runTransactionally(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    coroutineContext.connection.runWithManuelCommit {
        val transaction = CoroutineTransaction()

        try {
            val result = withContext(transaction) {
                block()
            }

            commit()
            return result
        } catch (e: Throwable) {
            rollback()
            throw e
        } finally {
            transaction.complete()
        }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T> Connection.runWithManuelCommit(block: Connection.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val before = autoCommit
    return try {
        autoCommit = false
        run(block)
    } finally {
        autoCommit = before
    }
}