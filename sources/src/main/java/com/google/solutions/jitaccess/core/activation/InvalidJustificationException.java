package com.google.solutions.jitaccess.core.activation;

import com.google.solutions.jitaccess.core.AccessDeniedException;

public class InvalidJustificationException extends AccessDeniedException {
  public InvalidJustificationException(String message) {
    super(message);
  }
}
