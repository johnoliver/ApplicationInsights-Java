plugins {
  id("ai.smoke-test-jar")
}

dependencies {
  implementation("com.microsoft.jeg.sre:microsoft-java-diagnostics:4.0.4-SNAPSHOT")
  // MockExtension is loaded as a separate agent extension, not bundled in the app
  compileOnly(project(":smoke-tests:apps:DiagnosticExtension:MockExtension"))
  testImplementation(project(":smoke-tests:framework"))
  implementation("org.springframework.boot:spring-boot-starter-web:2.5.12")
}
