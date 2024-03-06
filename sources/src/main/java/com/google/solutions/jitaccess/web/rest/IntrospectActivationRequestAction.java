package com.google.solutions.jitaccess.web.rest;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.Exceptions;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.TokenSigner;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.LogEvents;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.TokenObfuscator;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntrospectActivationRequestAction extends AbstractActivationAction {
  private final @NotNull TokenSigner tokenSigner;

  public IntrospectActivationRequestAction(
    @NotNull LogAdapter logAdapter,
    @NotNull RuntimeEnvironment runtimeEnvironment,
    @NotNull ProjectRoleActivator activator,
    @NotNull Instance<NotificationService> notificationServices,
    @NotNull TokenSigner tokenSigner
  ) {
    super(logAdapter, runtimeEnvironment, activator, notificationServices);
    this.tokenSigner = tokenSigner;
  }

  public @NotNull ResponseEntity execute(
    @NotNull IapPrincipal iapPrincipal,
    @Nullable String obfuscatedActivationToken
  ) throws AccessException {

    Preconditions.checkArgument(
      obfuscatedActivationToken != null && !obfuscatedActivationToken.trim().isEmpty(),
      "An activation token is required");

    var activationToken = TokenObfuscator.decode(obfuscatedActivationToken);

    try {
      var activationRequest = this.tokenSigner.verify(
        this.activator.createTokenConverter(),
        activationToken);

      if (!activationRequest.requestingUser().equals(iapPrincipal.email()) &&
        !activationRequest.reviewers().contains(iapPrincipal.email())) {
        throw new AccessDeniedException("The calling user is not authorized to access this approval request");
      }

      return new ResponseEntity(
        iapPrincipal.email(),
        activationRequest,
        Entitlement.Status.ACTIVATION_PENDING);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_GET_REQUEST,
          String.format("Accessing the activation request failed: %s", Exceptions.getFullMessage(e)))
        .addLabels(le -> addLabels(le, e))
        .write();

      throw new AccessDeniedException("Accessing the activation request failed");
    }
  }
}
