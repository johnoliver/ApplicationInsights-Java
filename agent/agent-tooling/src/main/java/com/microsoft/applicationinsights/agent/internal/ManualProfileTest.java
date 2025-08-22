// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.applicationinsights.agent.internal;

import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.DefaultRedirectStrategy;
import com.azure.core.http.policy.RedirectPolicy;
import com.azure.core.http.policy.TimeoutPolicy;
import com.azure.monitor.opentelemetry.autoconfigure.implementation.configuration.ConnectionString;
import com.azure.monitor.opentelemetry.autoconfigure.implementation.statsbeat.RpAttachType;
import com.azure.monitor.opentelemetry.autoconfigure.implementation.statsbeat.StatsbeatModule;
import com.azure.monitor.opentelemetry.autoconfigure.implementation.utils.PropertyHelper;
import com.microsoft.applicationinsights.agent.internal.httpclient.LazyHttpClient;
import com.microsoft.applicationinsights.agent.internal.profiler.service.ServiceProfilerClient;
import com.microsoft.applicationinsights.agent.internal.profiler.triggers.AlertingSubsystemInit;
import com.microsoft.applicationinsights.agent.internal.profiler.upload.UploadService;
import com.microsoft.applicationinsights.agent.internal.telemetry.TelemetryClient;
import com.microsoft.applicationinsights.alerting.alert.AlertBreach;
import com.microsoft.applicationinsights.alerting.config.AlertConfiguration;
import com.microsoft.applicationinsights.alerting.config.AlertMetricType;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class ManualProfileTest {
  private ManualProfileTest() {
    // Prevent instantiation
  }

  public static void main(String[] args) {
    run();
  }

  public static void run() {
    try {
      testProfileUpload();
      Thread.sleep(60000);
      System.out.println("COMPLETE");
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException(e);
    }
    System.exit(0);
  }

  private static void testProfileUpload() throws IOException, InterruptedException {
    RpAttachType.setRpAttachType(RpAttachType.INTEGRATED_AUTO);
    StatsbeatModule statsbeatModule =
        new StatsbeatModule(PropertyHelper::lazyUpdateVmRpIntegration);

    String csString = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");

    ConnectionString connectionString = ConnectionString.parse(csString);

    TelemetryClient telemetryClient =
        TelemetryClient.builder()
            .setConnectionStrings(csString)
            .setCustomDimensions(new HashMap<>())
            .setRoleName("MSFT-TEST-ROLE")
            .setRoleInstance("MSFT-TEST-INSTANCE")
            .setStatsbeatModule(statsbeatModule)
            .setGeneralExportQueueSize(2048)
            .build();

    telemetryClient.setOtelResource(
        Resource.create(
            Attributes.builder()
                .put("service.name", "unknown_service:java")
                .put("telemetry.sdk.language", "java")
                .put("telemetry.sdk.name", "opentelemetry")
                .put("telemetry.sdk.version", "1.51.0")
                .build()));

    HttpPipeline httpPipeline =
        LazyHttpClient.newHttpPipeLine(
            null,
            () -> "",
            new RedirectPolicy(
                new DefaultRedirectStrategy(
                    3,
                    "Location",
                    new HashSet<>(
                        Arrays.asList(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST)))),
            new TimeoutPolicy(Duration.ofSeconds(10)));

    ServiceProfilerClient serviceProfilerClient =
        new ServiceProfilerClient(
            connectionString.getProfilerEndpoint(),
            connectionString.getInstrumentationKey(),
            httpPipeline,
            null);

    UploadService uploadService =
        new UploadService(
            serviceProfilerClient,
            ignore -> {},
            "TEST-MACHINE",
            "1",
            connectionString::getInstrumentationKey,
            "TEST-ROLE");

    File tmpFile = createFakeProfile();

    for (int i = 0; i < 1; i++) {
      CountDownLatch cdl = new CountDownLatch(1);

      uploadService.upload(
          AlertBreach.builder()
              .setType(AlertMetricType.MANUAL)
              .setProfileId(UUID.randomUUID().toString())
              .setMemoryUsage(0.0)
              .setCpuMetric(0.0)
              .setAlertValue(0.0)
              .setAlertConfiguration(
                  AlertConfiguration.builder()
                      .setProfileDurationSeconds(60)
                      .setEnabled(true)
                      .setCooldownSeconds(60)
                      .setType(AlertMetricType.MANUAL)
                      .build())
              .build(),
          Instant.now().toEpochMilli(),
          tmpFile,
          done -> {
            AlertingSubsystemInit.sendServiceProfilerIndex(done, telemetryClient);
            cdl.countDown();
          });

      cdl.await();
    }
  }

  private static File createFakeProfile() throws IOException {
    File tmpFile = File.createTempFile("test-profile", ".jfr");
    tmpFile.deleteOnExit();
    try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
      fos.write("jfr profile data".getBytes(Charset.defaultCharset()));
      fos.flush();
    }
    return tmpFile;
  }
}
