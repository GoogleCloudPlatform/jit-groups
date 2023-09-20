package com.google.solutions.jitaccess.core.adapters;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.SlackAdapter.Options;
import com.google.solutions.jitaccess.core.adapters.SlackAdapter.SlackException;
import com.google.solutions.jitaccess.core.data.UserId;
import com.slack.api.RequestConfigurator;
import com.slack.api.Slack;
import com.slack.api.methods.Methods;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.User;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TestSlackAdapter {

  @Test
  public void WhenUserIdNotFound_ThenSlackMessageNotSent()
      throws SlackException, SlackApiException, IOException, AccessException {
    var secretManagerAdapter = Mockito.mock(SecretManagerAdapter.class);
    Mockito.doReturn("token").when(secretManagerAdapter).accessSecret(Mockito.anyString());
    var slackClient = Mockito.mock(Slack.class);
    var methodsClient = Mockito.mock(MethodsClient.class);
    var usersLookupByEmailResponse = Mockito.mock(UsersLookupByEmailResponse.class);
    Mockito.doReturn(false).when(usersLookupByEmailResponse).isOk();
    Mockito.doReturn(usersLookupByEmailResponse).when(methodsClient).usersLookupByEmail(Mockito.any(
        RequestConfigurator.class));
    Mockito.doReturn(methodsClient).when(slackClient).methods(Mockito.anyString());

    var adapter = new SlackAdapter(secretManagerAdapter, slackClient, new SlackAdapter.Options("/secretPath"));

    adapter.sendSlackMessage(List.of(new UserId("tyler@apexfintechsolutions.com")), "messageContent");

    Mockito.verify(methodsClient, Mockito.never()).chatPostMessage(Mockito.any(RequestConfigurator.class));

  }

  @Test
  public void WhenUserIdFound_ThenSlackMessageSent()
      throws SlackException, SlackApiException, IOException, AccessException {
    var secretManagerAdapter = Mockito.mock(SecretManagerAdapter.class);
    Mockito.doReturn("token").when(secretManagerAdapter).accessSecret(Mockito.anyString());
    var slackClient = Mockito.mock(Slack.class);
    var methodsClient = Mockito.mock(MethodsClient.class);
    var usersLookupByEmailResponse = Mockito.mock(UsersLookupByEmailResponse.class);
    Mockito.doReturn(true).when(usersLookupByEmailResponse).isOk();
    var user = Mockito.mock(User.class);
    Mockito.doReturn("1").when(user).getId();
    Mockito.doReturn(user).when(usersLookupByEmailResponse).getUser();
    Mockito.doReturn(usersLookupByEmailResponse).when(methodsClient).usersLookupByEmail(Mockito.any(
        RequestConfigurator.class));
    Mockito.doReturn(methodsClient).when(slackClient).methods(Mockito.anyString());

    var adapter = new SlackAdapter(secretManagerAdapter, slackClient, new SlackAdapter.Options("/secretPath"));

    adapter.sendSlackMessage(List.of(new UserId("tyler@apexfintechsolutions.com")), "messageContent");

    Mockito.verify(methodsClient).chatPostMessage(Mockito.any(RequestConfigurator.class));

  }
}
