package com.google.solutions.jitaccess.core.services;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.adapters.PubSubAdapter;

/**
 * Concrete class that delivers notifications over Pub/Sub.
 */
public class PubSubNotificationService extends NotificationService {
  private final PubSubAdapter adapter;
  private final Options options;

  public PubSubNotificationService(
    PubSubAdapter adapter,
    Options options
  ) {
    Preconditions.checkNotNull(adapter, "adapter");
    Preconditions.checkNotNull(options, "options");
    Preconditions.checkNotNull(options.topicResourceName, "options");

    this.adapter = adapter;
    this.options = options;
  }

  // -------------------------------------------------------------------------
  // NotificationService implementation.
  // -------------------------------------------------------------------------

  @Override
  public boolean canSendNotifications() {
    return true;
  }

  @Override
  public void sendNotification(Notification notification) throws NotificationException {
    // TODO: format & publish Pub/Sub message.
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  /**
   * @param topicResourceName PubSub topic in format projects/{project}/topics/{topic}
   */
  public record Options(String topicResourceName) {
  }

}
