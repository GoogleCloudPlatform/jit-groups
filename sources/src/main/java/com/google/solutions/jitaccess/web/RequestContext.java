package com.google.solutions.jitaccess.web;

import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.catalog.auth.*;
import jakarta.enterprise.context.RequestScoped;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

@RequestScoped
public class RequestContext {
  /**
   * Pseudo-subject for unauthenticated users.
   */
  private static final @NotNull Subject ANONYMOUS_SUBJECT = new Subject() {
    @Override
    public @NotNull EndUserId user() {
      return new EndUserId("anonymous");
    }

    @Override
    public @NotNull Set<Principal> principals() {
      return Set.of(new Principal(user()));
    }
  };

  private final @NotNull AuthenticationContext authenticationContext;
  private final @NotNull SubjectResolver subjectResolver;

  private @Nullable String requestMethod;
  private @Nullable String requestPath;
  private @Nullable String requestTraceId;

  public RequestContext(@NotNull SubjectResolver subjectResolver) {
    this.authenticationContext = new AuthenticationContext();
    this.subjectResolver = subjectResolver;
  }

  /**
   * Initialize request details.
   */
  void initialize(
    @NotNull String method,
    @NotNull String path,
    @Nullable String traceId
  ) {
    this.requestMethod = method;
    this.requestPath = path;
    this.requestTraceId = traceId;
  }

  /**
   * Authenticate the request context.
   */
  void authenticate(EndUserId userId, Device device) {
    if (isAuthenticated()) {
      throw new IllegalStateException(
        "Request context has been authenticated before");
    }

    this.authenticationContext.subject = new Subject() {
      private @Nullable Set<Principal> cachedPrincipals;
      private final @NotNull Object cachedPrincipalsLock = new Object();

      @Override
      public @NotNull EndUserId user() {
        return userId;
      }

      @Override
      public @NotNull Set<Principal> principals() {
        //
        // Resolve lazily.
        //
        synchronized (this.cachedPrincipalsLock)
        {
          if (this.cachedPrincipals == null) {
            try {
              this.cachedPrincipals = RequestContext.this.subjectResolver.resolve(this.user()).principals();
            }
            catch (AccessException | IOException e) {
              throw new UncheckedExecutionException(e);
            }
          }

          return this.cachedPrincipals;
        }
      }
    };
    this.authenticationContext.device = device;
  }

  boolean isAuthenticated() {
    return this.authenticationContext.subject != ANONYMOUS_SUBJECT;
  }

  public @NotNull Device device() {
    return this.authenticationContext.device;
  }

  public @NotNull Subject subject() {
    return this.authenticationContext;
  }

  public @NotNull EndUserId user() {
    return this.authenticationContext.user();
  }

  public @Nullable String requestMethod() {
    return this.requestMethod;
  }

  public @Nullable String requestPath() {
    return this.requestPath;
  }

  public @Nullable String requestTraceId() {
    return this.requestTraceId;
  }

  private static class AuthenticationContext implements Subject {
    private Subject subject = ANONYMOUS_SUBJECT;
    private @NotNull Device device = IapDevice.UNKNOWN;


    @Override
    public @NotNull EndUserId user() {
      return this.subject.user();
    }

    @Override
    public @NotNull Set<Principal> principals() {
      return this.subject.principals();
    }
  }
}
