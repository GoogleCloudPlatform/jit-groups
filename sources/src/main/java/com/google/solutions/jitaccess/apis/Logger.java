package com.google.solutions.jitaccess.apis;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface Logger {
  /**
   * Log an informational event
   * @param eventId unique ID for the event
   * @param message formatted message.
   */
  void info(
    @NotNull String eventId,
    @NotNull String message);

  /**
   * Log an informational event
   * @param eventId unique ID for the event
   * @param format message format.
   */
  void info(
    @NotNull String eventId,
    @NotNull String format,
    Object... args);

  /**
   * Log a warning event
   * @param eventId unique ID for the event
   * @param message formatted message.
   */
  void warn(
    @NotNull String eventId,
    @NotNull String message);

  /**
   * Log a warning event
   * @param eventId unique ID for the event
   * @param format message format.
   */
  void warn(
    @NotNull String eventId,
    @NotNull String format,
    Object... args);

  /**
   * Log a warning event
   * @param eventId unique ID for the event
   * @param exception exception
   */
  void warn(
    @NotNull String eventId,
    @NotNull Exception exception);

  /**
   * Log a warning event
   * @param eventId unique ID for the event
   * @param message formatted message.
   * @param exception exception
   */
  void warn(
    @NotNull String eventId,
    @NotNull String message,
    @NotNull Exception exception);

  /**
   * Log an error event
   * @param eventId unique ID for the event
   * @param message formatted message.
   */
  void error(
    @NotNull String eventId,
    @NotNull String message);

  /**
   * Log an error event
   * @param eventId unique ID for the event
   * @param format message format.
   */
  void error(
    @NotNull String eventId,
    @NotNull String format,
    Object... args);

  /**
   * Log an error event
   * @param eventId unique ID for the event
   * @param exception exception
   */
  void error(
    @NotNull String eventId,
    @NotNull Exception exception);

  /**
   * Log an error event
   * @param eventId unique ID for the event
   * @param message formatted message.
   * @param exception exception
   */
  void error(
    @NotNull String eventId,
    @NotNull String message,
    @NotNull Exception exception);

  /**
   * Create an informational event
   * @param eventId unique ID for the event
   */
  LogEntry buildInfo(@NotNull String eventId);

  /**
   * Create a warning event
   * @param eventId unique ID for the event
   */
  LogEntry buildWarning(@NotNull String eventId);

  /**
   * Create an error event
   * @param eventId unique ID for the event
   */
  LogEntry buildError(@NotNull String eventId);

  interface LogEntry {
    /**
     * Add custom label.
     */
    @NotNull LogEntry addLabel(@NotNull String label, @Nullable Object value);

    /**
     * Add custom labels.
     */
    @NotNull LogEntry addLabels(@NotNull Map<String, String> labels);

    /**
     * Set a message.
     */
    @NotNull LogEntry setMessage(@NotNull String message);

    /**
     * Set a formatted message.
     */
    @NotNull LogEntry setMessage(
      @NotNull String format,
      Object... args
    );

    /**
     * Emit the log entry.
     */
    void write();
  }
}
