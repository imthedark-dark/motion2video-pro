// Isolated from AGP: scanner dependencies cannot change the app build classpath.
plugins { id("org.owasp.dependencycheck") version "13.0.0" }
repositories { mavenCentral() }
dependencyCheck {
    scanSet.from("../../build/security-dependencies", "../../app/src/main/assets")
    formats = listOf("HTML", "JSON")
    failBuildOnCVSS = 7.0f
    failOnError = true
    System.getenv("NVD_API_KEY")?.takeIf { it.isNotBlank() }?.let { nvd.apiKey = it }
    analyzers.assemblyEnabled = false
    analyzers.nodeAudit.enabled = false // npm audit is a separate fail-closed CI step.
}
