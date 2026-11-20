// Isolated from AGP: scanner dependencies cannot change the app build classpath.
plugins { id("org.owasp.dependencycheck") version "13.0.0" }
repositories { mavenCentral() }
dependencyCheck {
    scanSet.from("../../build/security-dependencies", "../../app/src/main/assets")
    formats = listOf("HTML", "JSON")
    failBuildOnCVSS = 7.0f
    failOnError = true
    suppressionFile = file("suppressions.xml").absolutePath
    System.getenv("M2V_ODC_DATA")?.let { data.directory = it }
    val key = System.getenv("NVD_API_KEY")
    if (key.isNullOrBlank()) {
        nvd.datafeedUrl = "https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz"
    } else {
        nvd.apiKey = key
    }
    analyzers.assemblyEnabled = false
    analyzers.nodeAudit.enabled = false // npm audit is a separate fail-closed CI step.
}
