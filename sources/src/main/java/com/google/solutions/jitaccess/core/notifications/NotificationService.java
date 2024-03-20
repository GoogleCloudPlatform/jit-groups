//
// Copyright 2022 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.core.notifications;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.auth.UserId;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for notifying users about activation requests.
 */
@Singleton
public abstract class NotificationService {
  public abstract void sendNotification(Notification notification) throws NotificationException;

  public abstract boolean canSendNotifications();


  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  /**
   * Concrete class that prints notifications to STDOUT. Useful for local development only.
   */
  public static class SilentNotificationService extends NotificationService {
    private final boolean printToConsole;

    public SilentNotificationService(boolean printToConsole) {
      this.printToConsole = printToConsole;
    }

    @Override
    public boolean canSendNotifications() {
      return false;
    }

    @Override
    public void sendNotification(Notification notification) {
      if (this.printToConsole) {
        //
        // Print it so that we can see the message during development.
        //
        System.out.println(notification);
      }
    }
  }

  /**
   * Generic notification. The object contains the data for a notification,
   * but doesn't define its format.
   */
  public static abstract class Notification {
    private final @NotNull Collection<UserId> toRecipients;
    private final @NotNull Collection<UserId> ccRecipients;
    private final @NotNull String subject;

    protected final Map<String, Object> properties = new HashMap<>();

    protected boolean isReply() {
      return false;
    }

    public @NotNull Collection<UserId> getToRecipients() {
      return toRecipients;
    }

    public @NotNull Collection<UserId> getCcRecipients() {
      return ccRecipients;
    }

    public @NotNull String getSubject() {
      return subject;
    }

    /**
     * @return string identifying the type of notification.
     */
    public abstract String getType();

    protected Notification(
      @NotNull Collection<UserId> toRecipients,
      @NotNull Collection<UserId> ccRecipients,
      @NotNull String subject
    ) {
      Preconditions.checkNotNull(toRecipients, "toRecipients");
      Preconditions.checkNotNull(ccRecipients, "ccRecipients");
      Preconditions.checkNotNull(subject, "subject");

      this.toRecipients = toRecipients;
      this.ccRecipients = ccRecipients;
      this.subject = subject;
    }

    @Override
    public String toString() {
      return String.format(
        "Notification to %s: %s\n\n%s",
        this.toRecipients.stream().map(e -> e.email).collect(Collectors.joining(", ")),
        this.subject,
        this.properties
          .entrySet()
          .stream()
          .map(e -> String.format(" %s: %s", e.getKey(), e.getValue()))
          .collect(Collectors.joining("\n", "", "")));
    }
  }

  public static class NotificationException extends Exception {
    public NotificationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
