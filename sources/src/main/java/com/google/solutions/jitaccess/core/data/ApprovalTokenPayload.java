package com.google.solutions.jitaccess.core.data;

import com.google.api.client.json.webtoken.JsonWebToken;

import java.time.Instant;
import java.time.OffsetDateTime;

public class ApprovalTokenPayload {
  private final JsonWebToken.Payload payload;

  public ApprovalTokenPayload(JsonWebToken.Payload payload) {
    this.payload = payload;
  }

  public UserId getBeneficiary() {
    return new UserId(this.payload.get("requestor").toString());
  }

  public String getJustification() {
    return this.payload.get("justification").toString();
  }

  public RoleBinding getRoleBinding() {
    return new RoleBinding(
      this.payload.get("project").toString(),
      this.payload.get("role").toString());
  }

  public Instant getRequestTime() {
    return Instant.ofEpochSecond((Long)this.payload.get("iat"));
  }
}
