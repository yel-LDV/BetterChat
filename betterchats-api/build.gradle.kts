// API publica: sin dependencias y compilada para Java 8, asi cualquier plugin
// (incluidos los que aun compilan con target 1.8, como vClans) puede usarla.
plugins {
    `java-library`
    `maven-publish`
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.jar {
    archiveBaseName.set("BetterChats-API")
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "betterchats-api"
            from(components["java"])
        }
    }
}
