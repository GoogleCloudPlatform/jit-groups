//
// Copyright 2024 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.util.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic logger implementation that writes JSON-structured output.
 */
abstract class StructuredLogger implements Logger {
  private static final String LABEL_EXCEPTION_TRACE = "exception/stacktrace";
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  protected final @NotNull Appendable output;

  StructuredLogger(@NotNull Appendable output) {
    this.output = output;
  }

  protected @NotNull Map<String, String> createLabels(String eventId) {
    var labels = new HashMap<String, String>();
    labels.put("event/id", eventId);
    labels.put("event/type", "operational");
    return labels;
  }

  protected @Nullable String traceId() {
    return null;
  }

  //---------------------------------------------------------------------------
  // Logger.
  //---------------------------------------------------------------------------

  @Override
  public LogEntry buildInfo(@NotNull String eventId) {
    return new JsonLogEntry("INFO", createLabels(eventId), traceId());
  }

  @Override
  public LogEntry buildWarning(@NotNull String eventId) {
    return new JsonLogEntry("WARN", createLabels(eventId), traceId());
  }

  @Override
  public LogEntry buildError(@NotNull String eventId) {
    return new JsonLogEntry("ERROR", createLabels(eventId), traceId());
  }

  @Override
  public void info(
    @NotNull String eventId,
    @NotNull String message
  ) {
    buildInfo(eventId)
      .setMessage(message)
      .write();
  }

  @Override
  public void info(
    @NotNull String eventId,
    @NotNull String format,
    Object... args
  ) {
    buildInfo(eventId)
      .setMessage(format, args)
      .write();
  }

  @Override
  public void warn(
    @NotNull String eventId,
    @NotNull String message
  ) {
    buildWarning(eventId)
      .setMessage(message)
      .write();
  }

  @Override
  public void warn(
    @NotNull String eventId,
    @NotNull String format,
    Object... args
  ) {
    buildWarning(eventId)
      .setMessage(format, args)
      .write();
  }

  @Override
  public void warn(
    @NotNull String eventId,
    @NotNull Exception exception
  ) {
    buildWarning(eventId)
      .addLabel(LABEL_EXCEPTION_TRACE, Exceptions.stackTrace(exception))
      .setMessage(Exceptions.fullMessage(exception))
      .write();
  }

  @Override
  public void warn(
    @NotNull String eventId,
    @NotNull String message,
    @NotNull Exception exception
  ) {
    buildWarning(eventId)
      .addLabel(LABEL_EXCEPTION_TRACE, Exceptions.stackTrace(exception))
      .setMessage("%s: %s", message, Exceptions.fullMessage(exception))
      .write();
  }

  @Override
  public void error(
    @NotNull String eventId,
    @NotNull String message
  ) {
    buildError(eventId)
      .setMessage(message)
      .write();
  }

  @Override
  public void error(
    @NotNull String eventId,
    @NotNull String format,
    Object... args
  ) {
    buildError(eventId)
      .setMessage(format, args)
      .write();
  }

  @Override
  public void error(
    @NotNull String eventId,
    @NotNull Exception exception
  ) {
    buildError(eventId)
      .addLabel(LABEL_EXCEPTION_TRACE, Exceptions.stackTrace(exception))
      .setMessage(Exceptions.fullMessage(exception))
      .write();
  }

  @Override
  public void error(
    @NotNull String eventId,
    @NotNull String message,
    @NotNull Exception exception
  ) {
    buildError(eventId)
      .addLabel(LABEL_EXCEPTION_TRACE, Exceptions.stackTrace(exception))
      .setMessage("%s: %s", message, Exceptions.fullMessage(exception))
      .write();
  }

  //---------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------

  /**
   * Entry that, when serialized to JSON, can be parsed and interpreted by Cloud Logging.
   */
  @SuppressWarnings("FieldCanBeLocal")
  public class JsonLogEntry implements LogEntry {
    @JsonProperty("severity")
    private final @NotNull String severity;

    @JsonProperty("logging.googleapis.com/labels")
    private final @NotNull Map<String, String> labels;

    @JsonProperty("message")
    private @Nullable String message;

    @JsonProperty("logging.googleapis.com/trace")
    private final @Nullable String traceId;

    private JsonLogEntry(
      @NotNull String severity,
      @NotNull Map<String, String> labels,
      @Nullable String traceId
    ) {
      this.severity = severity;
      this.labels = labels;
      this.traceId = traceId;
    }

    @Override
    public @NotNull LogEntry addLabel(@NotNull String label, @Nullable Object value) {
      if (value != null) {
        this.labels.put(label, value.toString());
      }
      return this;
    }

    @Override
    public @NotNull LogEntry addLabels(@NotNull Map<String, String> labels) {
      this.labels.putAll(labels);
      return this;
    }

    @Override
    public @NotNull LogEntry setMessage(@NotNull String message) {
      this.message = message;
      return this;
    }

    @Override
    public @NotNull LogEntry setMessage(@NotNull String format, Object... args) {
      this.message = new Formatter()
        .format(format, args)
        .toString();
      return this;
    }

    @Override
    public void write() {
      try {
        StructuredLogger.this.output
          .append(JSON_MAPPER.writeValueAsString(this))
          .append("\n");
      }
      catch (IOException e) {
        try {
          StructuredLogger.this.output
            .append(String.format("Failed to log: %s\n", this.message));
        }
        catch (IOException ignored) {
        }
      }
    }
  }

  /**
   * Logger for operations that run in the context of
   * the application.
   */
  static class ApplicationContextLogger extends StructuredLogger {
    @SuppressWarnings("SameParameterValue")
    ApplicationContextLogger(@NotNull Appendable output) {
      super(output);
    }
  }

  /**
   * Logger for operations that run in the context of
   * a user request.
   */
  static class RequestContextLogger extends StructuredLogger {
    private final @NotNull RequestContext requestContext;

    RequestContextLogger(
      @NotNull Appendable output,
      @NotNull RequestContext requestContext
    ) {
      super(output);
      this.requestContext = requestContext;
    }

    RequestContextLogger(@NotNull RequestContext requestContext) {
      this(System.out, requestContext);
    }

    @Override
    protected @Nullable String traceId() {
      return this.requestContext.requestTraceId();
    }

    @Override
    protected @NotNull Map<String, String> createLabels(String eventId) {
      var labels = super.createLabels(eventId);

      if (this.requestContext.isAuthenticated()) {
        labels.put("auth/user_id", this.requestContext.user().email);
        labels.put("auth/directory", this.requestContext.subject().directory().toString());
        labels.put("auth/device_id", this.requestContext.device().deviceId());
        labels.put("auth/device_access_levels",
          String.join(", ", this.requestContext.device().accessLevels()));
      }

      labels.put("request/method", this.requestContext.requestMethod());
      labels.put("request/path", this.requestContext.requestPath());

      return labels;
    }
  }
}
