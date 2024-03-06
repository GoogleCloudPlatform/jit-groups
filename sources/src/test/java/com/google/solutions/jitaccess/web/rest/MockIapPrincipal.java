package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.web.iap.DeviceInfo;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import org.jetbrains.annotations.NotNull;

public class MockIapPrincipal implements IapPrincipal { // TODO: Merge into mocks
  private final @NotNull UserEmail userEmail;

  public MockIapPrincipal(@NotNull UserEmail userEmail) {
    this.userEmail = userEmail;
  }

  @Override
  public UserEmail email() {
    return this.userEmail;
  }

  @Override
  public String subjectId() {
    return "mock";
  }

  @Override
  public DeviceInfo device() {
    return DeviceInfo.UNKNOWN;
  }

  @Override
  public String getName() {
    return "mock@example.com";
  }
}