plugins {
  id("ai.smoke-test-jar")
}

dependencies {
  implementation(project(":smoke-tests:apps:Diagnostics:JfrFileReader"))

  implementation("com.microsoft.jeg.sre:microsoft-java-diagnostics:4.0.4-SNAPSHOT")
  testImplementation(project(":smoke-tests:framework"))
  implementation("org.springframework.boot:spring-boot-starter-web:2.1.7.RELEASE")
}
