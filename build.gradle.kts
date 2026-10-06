plugins {
    java
}

group = "burpss"
version = findProperty("releaseVersion") ?: "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2026.7")
    testImplementation("net.portswigger.burp.extensions:montoya-api:2026.7")
}

tasks.withType<JavaCompile> {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveFileName.set("burp-snapshot-${project.version}.jar")
}

tasks.register<JavaExec>("samples") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("burpss.Samples")
    args(layout.buildDirectory.dir("samples").get().asFile.absolutePath)
}

tasks.register<JavaExec>("preview") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("burpss.ui.WindowPreview")
    args(layout.buildDirectory.dir("samples").get().asFile.absolutePath)
}

tasks.test {
    failOnNoDiscoveredTests = false
}

tasks.register<JavaExec>("demo") {
    val burpJar = providers.gradleProperty("burpJar")
        .getOrElse("/Applications/Burp Suite.app/Contents/Resources/app/burpsuite.jar")
    classpath = sourceSets["test"].runtimeClasspath + files(burpJar)
    mainClass.set("burpss.ui.VideoRecorder")
    args("full", layout.buildDirectory.file("demo/snapshot-demo.mp4").get().asFile.absolutePath)
}

tasks.register<JavaExec>("demoGif") {
    val burpJar = providers.gradleProperty("burpJar")
        .getOrElse("/Applications/Burp Suite.app/Contents/Resources/app/burpsuite.jar")
    classpath = sourceSets["test"].runtimeClasspath + files(burpJar)
    mainClass.set("burpss.ui.VideoRecorder")
    args("full-still", layout.buildDirectory.file("demo/snapshot-demo-still.mp4").get().asFile.absolutePath)
}

tasks.register<JavaExec>("promo") {
    val burpJar = providers.gradleProperty("burpJar")
        .getOrElse("/Applications/Burp Suite.app/Contents/Resources/app/burpsuite.jar")
    classpath = sourceSets["test"].runtimeClasspath + files(burpJar)
    mainClass.set("burpss.ui.VideoRecorder")
    args("whats-new", layout.buildDirectory.file("promo/snapshot-whats-new.mp4").get().asFile.absolutePath)
}

tasks.register<JavaExec>("libraryCheck") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("burpss.LibraryCheck")
}
