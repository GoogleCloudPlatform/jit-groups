package com.google.solutions.jitaccess.web.rest;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRole;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.TokenObfuscator;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractActivationAction extends AbstractAction {
  protected final @NotNull Instance<NotificationService> notificationServices;
  protected final @NotNull RuntimeEnvironment runtimeEnvironment;
  protected final @NotNull ProjectRoleActivator activator;

  public AbstractActivationAction(
    @NotNull LogAdapter logAdapter,
    @NotNull RuntimeEnvironment runtimeEnvironment,
    @NotNull ProjectRoleActivator activator,
    @NotNull Instance<NotificationService> notificationServices
  ) {
    super(logAdapter);
    this.notificationServices = notificationServices;
    this.runtimeEnvironment = runtimeEnvironment;
    this.activator = activator;
  }

  protected @NotNull URL createActivationRequestUrl(
    @NotNull UriInfo uriInfo,
    @NotNull ProjectId projectId,
    String activationToken
  ) throws MalformedURLException {
    Preconditions.checkNotNull(uriInfo);
    Preconditions.checkNotNull(activationToken);

    //
    // NB. Embedding the token verbatim can trigger overzealous phishing filters
    // to assume that we're embedding an access token (or some other form of
    // credential in the URL). But activation tokens aren't credentials, they
    // don't grant access to anything and the embedded information isn't
    // confidential.
    //
    // Obfuscate the token to avoid such false-flagging.
    //
    // NB. We include the project ID to force the approver into
    // the right scope. This isn't strictly necessary, but it
    // improves user experience.
    //
    return this.runtimeEnvironment
      .createAbsoluteUriBuilder(uriInfo)
      .path("/")
      .queryParam("activation", TokenObfuscator.encode(activationToken))
      .queryParam("projectId", projectId.id())
      .build()
      .toURL();
  }

  public static class ResponseEntity {
    public final UserEmail beneficiary;
    public final Collection<UserEmail> reviewers;
    public final boolean isBeneficiary;
    public final boolean isReviewer;
    public final String justification;
    public final @NotNull List<Item> items;

    public ResponseEntity(
      UserEmail caller,
      @NotNull ActivationRequest<ProjectRole> request,
      Entitlement.Status status
    ) {
      Preconditions.checkNotNull(request);

      this.beneficiary = request.requestingUser();
      this.isBeneficiary = request.requestingUser().equals(caller);
      this.justification = request.justification();
      this.items = request
        .entitlements()
        .stream()
        .map(ent -> new Item(
          request.id(),
          ent.roleBinding(),
          status,
          request.startTime(),
          request.endTime()))
        .collect(Collectors.toList());

      if (request instanceof MpaActivationRequest<ProjectRole> mpaRequest) {
        this.reviewers = mpaRequest.reviewers();
        this.isReviewer = mpaRequest.reviewers().contains(caller);
      }
      else {
        this.reviewers = Set.of();
        this.isReviewer = false;
      }
    }

    public static class Item {
      public final String activationId;
      public final String projectId;
      public final @NotNull RoleBinding roleBinding;
      public final Entitlement.Status status;
      public final long startTime;
      public final long endTime;

      private Item(
        @NotNull ActivationId activationId,
        @NotNull RoleBinding roleBinding,
        Entitlement.Status status,
        @NotNull Instant startTime,
        @NotNull Instant endTime
      ) {
        assert endTime.isAfter(startTime);

        this.activationId = activationId.toString();
        this.projectId = ProjectId.parse(roleBinding.fullResourceName()).id();
        this.roleBinding = roleBinding;
        this.status = status;
        this.startTime = startTime.getEpochSecond();
        this.endTime = endTime.getEpochSecond();
      }
    }
  }
}
