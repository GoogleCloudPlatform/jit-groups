package com.google.solutions.jitaccess.core.notifications;

import com.google.api.client.json.GenericJson;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.clients.PubSubClient;
import com.google.solutions.jitaccess.core.clients.PubSubTopic;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Concrete class that delivers notifications over Pub/Sub.
 */
public class PubSubNotificationService extends NotificationService {
  private final @NotNull PubSubClient adapter;
  private final @NotNull Options options;

  public PubSubNotificationService(
    @NotNull PubSubClient adapter,
    @NotNull Options options
  ) {
    Preconditions.checkNotNull(adapter, "adapter");
    Preconditions.checkNotNull(options, "options");
    Preconditions.checkNotNull(options.topic, "options");

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
  public void sendNotification(@NotNull Notification notification) throws NotificationException {
    var attributes = new GenericJson();
    for (var property : notification.properties.entrySet())
    {
      Object propertyValue;
      if (property.getValue() instanceof Instant) {
        //
        // Serialize ISO-8601 representation instead of individual
        // object fields.
        //
        propertyValue = ((Instant)property.getValue()).toString();
      }
      else if (property.getValue() instanceof Collection<?>) {
        propertyValue = ((Collection<?>)property.getValue()).stream()
          .map(i -> i.toString())
          .collect(Collectors.toList());
      }
      else {
        propertyValue = property.getValue().toString();
      }

      attributes.set(property.getKey().toLowerCase(), propertyValue);
    }

    var payload = new GenericJson()
      .set("type", notification.getType())
      .set("attributes", attributes);

    var payloadAsJson = new Gson().toJson(payload);

    try {
      var message = new PubsubMessage()
        .encodeData(payloadAsJson.getBytes(StandardCharsets.UTF_8));

      this.adapter.publish(options.topic, message);
    } catch (AccessException | IOException e){
      throw new NotificationException("Publishing event to Pub/Sub failed", e);
    }
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  /**
   * @param topicResourceName PubSub topic in format projects/{project}/topics/{topic}
   */
  public record Options(PubSubTopic topic) {
  }
}
