plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("utsjekk.AppKt")
}

val ktorVersion = "3.0.0"
val libVersion = "2.0.0"
val kontraktVersion = "1.0_20241022151447_30ecf61"

dependencies {
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:jdbc:$libVersion")
    implementation("no.nav.helved:job:$libVersion")
    implementation("no.nav.helved:kafka:$libVersion")
    implementation("no.nav.helved:task:$libVersion")

    implementation("no.nav.utsjekk.kontrakter:oppdrag:$kontraktVersion")
    implementation("no.nav.utsjekk.kontrakter:iverksett:$kontraktVersion")
    implementation("no.nav.utsjekk.kontrakter:felles:$kontraktVersion")

    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.20")
    implementation("io.getunleash:unleash-client-java:9.2.4")

    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("no.nav.helved:auth-test:$libVersion")
    testImplementation("no.nav.helved:jdbc-test:$libVersion")
}
