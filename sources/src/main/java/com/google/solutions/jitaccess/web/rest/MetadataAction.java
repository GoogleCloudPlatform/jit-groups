package com.google.solutions.jitaccess.web.rest;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.ApplicationVersion;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.enterprise.context.Dependent;
import org.jetbrains.annotations.NotNull;

/**
 * Get information about this instance of JIT Access.
 */
@Dependent
public class MetadataAction extends AbstractAction {
  private final @NotNull MpaProjectRoleCatalog catalog;
  private final @NotNull JustificationPolicy justificationPolicy;

  public MetadataAction(
    @NotNull LogAdapter logAdapter,
    @NotNull MpaProjectRoleCatalog catalog,
    @NotNull JustificationPolicy justificationPolicy
  ) {
    super(logAdapter);
    this.catalog = catalog;
    this.justificationPolicy = justificationPolicy;
  }

  public @NotNull MetadataAction.ResponseEntity execute(
    @NotNull IapPrincipal iapPrincipal
  ) {
    var options = this.catalog.options();
    return new ResponseEntity(
      justificationPolicy.hint(),
      iapPrincipal.email(),
      ApplicationVersion.VERSION_STRING,
      (int)options.maxActivationDuration().toMinutes(),
      Math.min(60, (int)options.maxActivationDuration().toMinutes()));
  }

  public static class ResponseEntity { // TODO: make record
    public final @NotNull String justificationHint;
    public final @NotNull UserEmail signedInUser;
    public final @NotNull String applicationVersion;
    public final int defaultActivationTimeout; // in minutes.
    public final int maxActivationTimeout;     // in minutes.

    private ResponseEntity(
      @NotNull String justificationHint,
      @NotNull UserEmail signedInUser,
      @NotNull String applicationVersion,
      int maxActivationTimeoutInMinutes,
      int defaultActivationTimeoutInMinutes
    ) {
      Preconditions.checkNotNull(justificationHint, "justificationHint");
      Preconditions.checkNotNull(signedInUser, "signedInUser");
      Preconditions.checkArgument(defaultActivationTimeoutInMinutes > 0, "defaultActivationTimeoutInMinutes");
      Preconditions.checkArgument(maxActivationTimeoutInMinutes > 0, "maxActivationTimeoutInMinutes");
      Preconditions.checkArgument(maxActivationTimeoutInMinutes >= defaultActivationTimeoutInMinutes, "maxActivationTimeoutInMinutes");

      this.justificationHint = justificationHint;
      this.signedInUser = signedInUser;
      this.applicationVersion = applicationVersion;
      this.defaultActivationTimeout = defaultActivationTimeoutInMinutes;
      this.maxActivationTimeout = maxActivationTimeoutInMinutes;
    }
  }
}
