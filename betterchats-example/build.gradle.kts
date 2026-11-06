// Plugin cliente de demostracion: solo depende de la API (como haria un tercero).
val spigotVersion: String by rootProject.extra

dependencies {
    compileOnly(project(":betterchats-api"))
    compileOnly("org.spigotmc:spigot-api:$spigotVersion") {
        exclude(group = "net.md-5", module = "bungeecord-chat")
    }
}

tasks.jar {
    archiveBaseName.set("BetterChats-Example")
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("plugin.yml") {
        expand("version" to v)
    }
}
