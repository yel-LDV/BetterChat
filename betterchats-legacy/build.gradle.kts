// Adaptador Spigot/Paper antiguo (probado contra la API 1.8.8). Adventure va
// incluido (shaded) y reubicado, igual que Gson, para no chocar con otros
// plugins que traen su propia copia (EzChat, Apollo...).
plugins {
    id("com.gradleup.shadow")
}

val adventurePlatformVersion: String by rootProject.extra
val spigotVersion: String by rootProject.extra
val papiVersion: String by rootProject.extra

// PlaceholderAPI: si hay un jar en libs/ (el mismo que usa el servidor) se
// compila contra el; si no, se descarga del repositorio de extendedclip.
// Nunca se empaqueta: en runtime se usa el PlaceholderAPI instalado.
val localPapi = rootProject.fileTree("libs") { include("*laceholder*.jar", "PAPI*.jar") }

// PacketEvents (menu de /chatcolor): igual que PlaceholderAPI, jar de libs/ o
// repositorio de CodeMC. Solo compilacion; en runtime el plugin packetevents.
val localPacketEvents = rootProject.fileTree("libs") { include("packetevents*.jar") }
val packetEventsVersion = "2.12.1"

dependencies {
    implementation(project(":betterchats-core"))
    implementation("net.kyori:adventure-platform-bukkit:$adventurePlatformVersion")
    compileOnly("org.spigotmc:spigot-api:$spigotVersion") {
        // bungeecord-chat:1.8-SNAPSHOT ya no esta en los repos publicos.
        exclude(group = "net.md-5", module = "bungeecord-chat")
    }
    if (localPapi.isEmpty) {
        compileOnly("me.clip:placeholderapi:$papiVersion") {
            isTransitive = false
        }
    } else {
        compileOnly(localPapi)
    }
    if (localPacketEvents.isEmpty) {
        compileOnly("com.github.retrooper:packetevents-spigot:$packetEventsVersion") {
            isTransitive = false
        }
    } else {
        compileOnly(localPacketEvents)
    }
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("plugin.yml") {
        expand("version" to v)
    }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("BetterChats")
    archiveClassifier.set("")
    relocate("net.kyori", "dev.betterchats.libs.kyori")
    relocate("com.google.gson", "dev.betterchats.libs.gson")
    exclude("META-INF/maven/**", "META-INF/versions/**/module-info.class", "module-info.class")
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
