plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("oppdrag.AppKt")
}

val ktorVersion = "3.0.0"
val libVersion = "2.0.0"

dependencies {
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:jdbc:$libVersion")
    implementation("no.nav.helved:mq:$libVersion")

    implementation("no.nav.utsjekk.kontrakter:oppdrag:1.0_20241022151447_30ecf61")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.3")

    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("no.nav.helved:auth-test:$libVersion")
    testImplementation("no.nav.helved:jdbc-test:$libVersion")
    testImplementation("no.nav.helved:mq-test:$libVersion")
}
