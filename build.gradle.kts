plugins {
    java
}

group = "dev.xdark"
version = "1.0"

repositories.mavenCentral()

java.toolchain.languageVersion.set(JavaLanguageVersion.of(23))
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--enable-preview")
}
tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.jar.configure {
    manifest.attributes(
        "Premain-Class" to "dev.xdark.bsea.BlockSystemExitAgent",
        "Can-Retransform-Classes" to true,
    )
}
