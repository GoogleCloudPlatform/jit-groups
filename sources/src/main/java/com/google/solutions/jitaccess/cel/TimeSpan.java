package com.google.solutions.jitaccess.cel;

import java.time.Instant;

public record TimeSpan(Instant start, Instant end) {
  public TimeSpan {
    assert start.isBefore(end);
  }
}
