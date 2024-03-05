package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.catalog.ActivationRequest;
import com.google.solutions.jitaccess.core.catalog.EntitlementId;
import com.google.solutions.jitaccess.core.catalog.MpaActivationRequest;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.web.LogAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneOffset;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Base class for resource classes. A resource class models a REST
 * resource, but is independent of JAX-RS.
 */
public abstract class AbstractResource {
  protected final @NotNull LogAdapter logAdapter;

  protected AbstractResource(@NotNull LogAdapter logAdapter) {
    this.logAdapter = logAdapter;
  }

  // -------------------------------------------------------------------------
  // Logging helper methods.
  // -------------------------------------------------------------------------

  protected static <T extends EntitlementId> @NotNull LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull ActivationRequest<T> request
  ) {
    entry
      .addLabel("activation_id", request.id().toString())
      .addLabel("activation_start", request.startTime().atOffset(ZoneOffset.UTC).toString())
      .addLabel("activation_end", request.endTime().atOffset(ZoneOffset.UTC).toString())
      .addLabel("justification", request.justification())
      .addLabels(e -> addLabels(e, request.entitlements()));

    if (request instanceof MpaActivationRequest<T> mpaRequest) {
      entry.addLabel("reviewers", mpaRequest
        .reviewers()
        .stream()
        .map(u -> u.email)
        .collect(Collectors.joining(", ")));
    }

    return entry;
  }

  protected static LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull RoleBinding roleBinding
  ) {
    return entry
      .addLabel("role", roleBinding.role())
      .addLabel("resource", roleBinding.fullResourceName())
      .addLabel("project_id", ProjectId.parse(roleBinding.fullResourceName()).id());
  }

  protected static LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull Collection<? extends EntitlementId> entitlements
  ) {
    return entry.addLabel(
      "entitlements",
      entitlements.stream().map(s -> s.toString()).collect(Collectors.joining(", ")));
  }

  protected static LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull Exception exception
  ) {
    return entry.addLabel("error", exception.getClass().getSimpleName());
  }

  protected static LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull ProjectId project
  ) {
    return entry.addLabel("project", project.id());
  }
}
