plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("simulering.AppKt")
}

val ktorVersion = "3.0.0"
val libVersion = "2.0.0"

dependencies {
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:http:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:ws:$libVersion")
    implementation("no.nav.helved:xml:$libVersion")

    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-double-receive:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.3")

    implementation("jakarta.xml.ws:jakarta.xml.ws-api:4.0.2")
    implementation("com.sun.xml.ws:jaxws-rt:4.0.3")

    implementation("no.nav.utsjekk.kontrakter:oppdrag:1.0_20241022151447_30ecf61")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.helved:auth-test:$libVersion")
}
