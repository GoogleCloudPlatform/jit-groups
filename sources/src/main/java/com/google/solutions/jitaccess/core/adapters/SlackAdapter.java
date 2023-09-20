package com.google.solutions.jitaccess.core.adapters;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.data.UserId;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import java.io.IOException;
import java.util.Collection;

public class SlackAdapter {
  private final SecretManagerAdapter secretManagerAdapter;
  private final Options options;
  private final Slack slackClient;
  private final MethodsClient methods;


  public SlackAdapter(SecretManagerAdapter secretManagerAdapter, Slack slackClient, Options options)
      throws SlackException {
    Preconditions.checkNotNull(secretManagerAdapter, "secretManagerAdapter");
    Preconditions.checkNotNull(options, "options");

    this.secretManagerAdapter = secretManagerAdapter;
    this.slackClient = slackClient;
    this.options = options;

    String slackToken = retrieveSlackToken();
    this.methods = slackClient.methods(slackToken);
  }

  public void sendSlackMessage(
      Collection<UserId> recipients,
      String messageContent
  ) throws SlackException {
      for (UserId userId : recipients) {
        String email = userId.email.replace("apexfintechsolutions", "apexclearing");
        String id = userIdLookupByEmail(email);
        if (id != null) {
          chatPostMessage(id, messageContent);
        }
      }
  }


  private String userIdLookupByEmail(String email) throws SlackException {
    try {
      UsersLookupByEmailResponse response = methods.usersLookupByEmail(
          req -> req.email(email));
      if (response.isOk()) {
        return response.getUser().getId();
      } else {
        return null;
      }
    } catch (IOException | SlackApiException e) {
      throw new SlackException("Could not lookup user", e);
    }
  }

  private ChatPostMessageResponse chatPostMessage(String userId, String messageText)
      throws SlackException {
    try {
      return methods.chatPostMessage(req -> req.channel(userId).blocksAsString(messageText));
    } catch (IOException | SlackApiException e) {
      throw new SlackException("Unable to post Slack message", e);
    }
  }

  private String retrieveSlackToken() throws SlackException {
    try {
      return secretManagerAdapter.accessSecret(options.slackTokenSecretPath);
    } catch (AccessException | IOException e) {
      throw new SlackException("Could not retrieve Slack token", e);
    }
  }

  public static class Options {
    private final String slackTokenSecretPath;

    public Options(String slackTokenSecretPath) {
      this.slackTokenSecretPath = slackTokenSecretPath;
    }
  }

  public static class SlackException extends Exception {

    public SlackException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
