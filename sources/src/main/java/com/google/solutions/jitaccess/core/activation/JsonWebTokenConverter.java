package com.google.solutions.jitaccess.core.activation;

import com.google.api.client.json.webtoken.JsonWebToken;

public interface JsonWebTokenConverter<T> {
  /**
   * Convert object to JWT payload.
   */
  JsonWebToken.Payload convert(T object);

  /**
   * Create JWT payload to object.
   */
  T convert(JsonWebToken.Payload payload);
}
