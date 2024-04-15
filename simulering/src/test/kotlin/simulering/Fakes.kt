package simulering

import simulering.ws.SamlToken
import simulering.ws.Soap
import simulering.ws.SoapXml
import simulering.ws.Sts
import java.net.URI
import java.time.LocalDateTime
import java.util.*

class FakeSts : Sts {
    override suspend fun samlToken(): SamlToken {
        return SamlToken(
            "very-secure token",
            LocalDateTime.now(),
        )
    }
}

class FakeSoap private constructor(private val resource: String) : Soap {
    companion object {
        fun with(
            resource: String,
            updateResource: (String) -> String = { it },
        ): FakeSoap {
            return FakeSoap(updateResource(resource))
        }
    }

    override suspend fun call(action: String, body: String): String {
        val xml = SoapXml.envelope(
            action = action,
            messageId = UUID.randomUUID(),
            serviceUrl = "http://localhost:8083".let(::URI).toURL(),
            assertion = "very-secure token",
            body = resource,
        )
        return xml
    }
}

internal fun resources(filename: String): String =
    {}::class.java.getResource(filename)!!.openStream().bufferedReader().readText()

