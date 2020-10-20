/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.jdbc.DbSystem;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.apache.geode.cache.Region;

public class GeodeTracer extends DatabaseClientTracer<Region<?, ?>, String> {
  public static final GeodeTracer TRACER = new GeodeTracer();

  public Span startSpan(String operation, Region<?, ?> connection, String query) {
    String normalizedQuery = normalizeQuery(query);

    Span span =
        tracer
            .spanBuilder(operation)
            .setSpanKind(CLIENT)
            .setAttribute(SemanticAttributes.DB_SYSTEM, dbSystem(connection))
            .setAttribute(SemanticAttributes.DB_OPERATION, operation)
            .startSpan();

    onConnection(span, connection);
    setNetSemanticConvention(span, connection);
    onStatement(span, normalizedQuery);

    return span;
  }

  @Override
  protected String normalizeQuery(String query) {
    return query;
  }

  @Override
  protected String dbSystem(Region<?, ?> region) {
    return DbSystem.GEODE;
  }

  @Override
  protected String dbName(Region<?, ?> region) {
    return region.getName();
  }

  @Override
  protected InetSocketAddress peerAddress(Region<?, ?> region) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.geode-1.7";
  }
}
