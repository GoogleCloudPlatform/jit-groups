package com.google.solutions.jitaccess.web.rest;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.Exceptions;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.catalog.Activation;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRole;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.LogEvents;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.enterprise.inject.Instance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Request and self-approve one or more project roles.
 * Only allowed for JIT-eligible roles.
 */
public class RequestAndSelfApproveAction extends AbstractActivationAction {
  private final @NotNull MpaProjectRoleCatalog catalog;

  public RequestAndSelfApproveAction(
    @NotNull LogAdapter logAdapter,
    @NotNull RuntimeEnvironment runtimeEnvironment,
    @NotNull ProjectRoleActivator activator,
    @NotNull Instance<NotificationService> notificationServices,
    @NotNull MpaProjectRoleCatalog catalog
  ) {
    super(logAdapter, runtimeEnvironment, activator, notificationServices);
    this.catalog = catalog;
  }

  public @NotNull ResponseEntity execute(
    @NotNull IapPrincipal iapPrincipal,
    @Nullable String projectIdString,
    @NotNull RequestEntity request
  ) throws AccessDeniedException {
    Preconditions.checkArgument(
      projectIdString != null && !projectIdString.trim().isEmpty(),
      "You must provide a projectId");
    Preconditions.checkArgument(
      request != null && request.roles != null && request.roles.size() > 0,
      "Specify one or more roles to activate");

    // TODO: Move check to EntitlementActivator
    //    Preconditions.checkArgument(
//      request != null && request.roles != null && request.roles.size() <= this.options.maxNumberOfJitRolesPerSelfApproval,
//      String.format(
//        "The number of roles exceeds the allowed maximum of %d",
//        this.options.maxNumberOfJitRolesPerSelfApproval));
    Preconditions.checkArgument(
      request.justification != null && request.justification.trim().length() > 0,
      "Provide a justification");
    Preconditions.checkArgument(
      request.justification != null && request.justification.length() < 100,
      "The justification is too long");

    var userContext = this.catalog.createContext(iapPrincipal.email());

    var projectId = new ProjectId(projectIdString);

    //
    // Create a JIT activation request.
    //
    var requestedRoleBindingDuration = Duration.ofMinutes(request.activationTimeout);
    var activationRequest = this.activator.createJitRequest(
      userContext,
      request.roles
        .stream()
        .map(r -> new ProjectRole(new RoleBinding(projectId.getFullResourceName(), r)))
        .collect(Collectors.toSet()),
      request.justification,
      Instant.now().truncatedTo(ChronoUnit.SECONDS),
      requestedRoleBindingDuration);

    try {
      //
      // Activate the request.
      //
      var activation = this.activator.activate(
        userContext,
        activationRequest);

      assert activation != null;

      //
      // Notify listeners, if any.
      //
      for (var service : this.notificationServices) {
        service.sendNotification(new ActivationSelfApprovedNotification(projectId, activation));
      }

      //
      // Leave an audit log trail.
      //
      this.logAdapter
        .newInfoEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format(
            "User %s activated roles %s on '%s' for themselves for %d minutes",
            iapPrincipal.email(),
            activationRequest.entitlements().stream()
              .map(ent -> String.format("'%s'", ent.roleBinding().role()))
              .collect(Collectors.joining(", ")),
            projectId.getFullResourceName(),
            requestedRoleBindingDuration.toMinutes()))
        .addLabels(le -> addLabels(le, activationRequest))
        .write();

      return new ResponseEntity(
        iapPrincipal.email(),
        activation.request(),
        Entitlement.Status.ACTIVE);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format(
            "User %s failed to activate roles %s on '%s' for themselves for %d minutes: %s",
            iapPrincipal.email(),
            activationRequest.entitlements().stream()
              .map(ent -> String.format("'%s'", ent.roleBinding().role()))
              .collect(Collectors.joining(", ")),
            projectId.getFullResourceName(),
            requestedRoleBindingDuration.toMinutes(),
            Exceptions.getFullMessage(e)))
        .addLabels(le -> addLabels(le, projectId))
        .addLabels(le -> addLabels(le, activationRequest))
        .addLabels(le -> addLabels(le, e))
        .write();

      if (e instanceof AccessDeniedException) {
        throw (AccessDeniedException)e.fillInStackTrace();
      }
      else {
        throw new AccessDeniedException("Activating role failed", e);
      }
    }
  }

  public static class RequestEntity {
    public List<String> roles;
    public String justification;
    public int activationTimeout; // in minutes.
  }

  /**
   * Notification indicating that a self-approval was performed.
   */
  public static class ActivationSelfApprovedNotification extends NotificationService.Notification {
    protected ActivationSelfApprovedNotification(
      ProjectId projectId,
      @NotNull Activation<ProjectRole> activation)
    {
      super(
        List.of(activation.request().requestingUser()),
        List.of(),
        String.format(
          "Activated roles %s on '%s'",
          activation.request().entitlements().stream()
            .map(ent -> String.format("'%s'", ent.roleBinding().role()))
            .collect(Collectors.joining(", ")),
          projectId));

      this.properties.put("BENEFICIARY", activation.request().requestingUser());
      this.properties.put("PROJECT_ID", projectId);
      this.properties.put("ROLE", activation.request()
        .entitlements()
        .stream()
        .map(ent -> ent.roleBinding().role())
        .collect(Collectors.joining(", ")));
      this.properties.put("START_TIME", activation.request().startTime());
      this.properties.put("END_TIME", activation.request().endTime());
      this.properties.put("JUSTIFICATION", activation.request().justification());
    }

    @Override
    protected boolean isReply() {
      return true;
    }

    @Override
    public @NotNull String getType() {
      return "ActivationSelfApproved";
    }
  }
}
