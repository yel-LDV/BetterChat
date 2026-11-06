// Logica agnostica de plataforma: sin Bukkit. Equipos, JSON, pipeline de
// formato seguro, persistencia, mensajes privados, anti-spam y spy.
plugins {
    `java-library`
}

val adventureVersion: String by rootProject.extra
val gsonVersion: String by rootProject.extra

dependencies {
    api(project(":betterchats-api"))
    api("net.kyori:adventure-api:$adventureVersion")
    api("net.kyori:adventure-text-minimessage:$adventureVersion")
    api("net.kyori:adventure-text-serializer-legacy:$adventureVersion")
    api("net.kyori:adventure-text-serializer-plain:$adventureVersion")
    // Solo para medir el tamano real del paquete de chat (PacketSafety).
    api("net.kyori:adventure-text-serializer-gson:$adventureVersion")
    api("com.google.code.gson:gson:$gsonVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
