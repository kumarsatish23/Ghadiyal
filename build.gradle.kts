plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.16.0"
}

group = "com.gherkin.navigator"
version = "1.0.0"

// Project metadata
extra["pluginName"] = "Ghadiyal"
extra["pluginDescription"] = "Professional Gherkin Navigator - Fast, precise navigation for Gherkin feature files"
extra["pluginAuthor"] = "Satish Kumar Rai (@kumarsatish23)"
extra["pluginWebsite"] = "https://github.com/kumarsatish23/gherkin-navigator-plugin"

repositories {
    mavenCentral()
}

dependencies {
    // No additional dependencies needed - IntelliJ SDK provides everything
}

intellij {
    version.set("2024.2")
    type.set("PY") // PyCharm Professional Edition
    plugins.set(listOf("gherkin")) // Depend on Gherkin plugin
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("252.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    // Optimize for fast incremental builds
    buildSearchableOptions {
        enabled = false
    }
    
    // Disable instrumentation to avoid JDK issues
    instrumentCode {
        enabled = false
    }
}

