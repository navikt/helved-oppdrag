package oppdrag.containers

import oppdrag.MQConfig
import org.testcontainers.containers.GenericContainer

class MQTestContainer : AutoCloseable {
    private val mq = GenericContainer<Nothing>("ibmcom/mq").apply {
        withEnv("LICENSE", "accept")
        withEnv("MQ_QMGR_NAME", "QM1")
        withExposedPorts(1414)
        start()
    }
    val config
        get() = MQConfig(
            host = "localhost",
            port = mq.firstMappedPort,
            channel = "DEV.ADMIN.SVRCONN",
            manager = "QM1",
            username = "admin",
            password = "passw0rd",
        )

    override fun close() {
        mq.stop()
    }
}