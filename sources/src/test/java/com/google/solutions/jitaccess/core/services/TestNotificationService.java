package com.google.solutions.jitaccess.core.services;

import com.google.solutions.jitaccess.core.adapters.MailAdapter;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestNotificationService {

  // -------------------------------------------------------------------------
  // sendNotification.
  // -------------------------------------------------------------------------

  //@Test
  public void whenSmtpConfigured_ThenSendNotificationSendsMail() throws Exception {
    String senderAddress = "...";
    String recipient = "...";
    String username = "...";
    String password = "...";

    var options = new MailAdapter.Options(
      "smtp.mailgun.org",
      587,
      "JIT Access Test",
      senderAddress);
    options.setSmtpCredentials(username, password);

    var adapter = new MailAdapter(options);
    var service = new NotificationService(
      adapter,
      new NotificationService.Options(true));

    var notification = new NotificationService.ApprovalRequest(
      new UserId("alice@example.com"),
      new UserId(recipient),
      new ProjectRole(
        new RoleBinding("//cloudresourcemanager.googleapis.com/projects/project-1", "project/browser"),
        ProjectRole.Status.ELIGIBLE_FOR_JIT),
      "I need it",
      new URI("https://github.com/GoogleCloudPlatform/jit-access"));
    service.sendNotification(notification);
  }

  @Test
  public void whenEmailEnabled_ThenSendNotificationSendsMail() throws Exception {
    var mailAdapter = Mockito.mock(MailAdapter.class);
    var service = new NotificationService(
      mailAdapter,
      new NotificationService.Options(true));
    service.sendNotification(new NotificationService.ApprovalRequest(
      new UserId("requestor@example.com"),
      new UserId("recipient@example.com"),
      new ProjectRole(
        new RoleBinding("//cloudresourcemanager.googleapis.com/projects/project-1", "project/browser"),
        ProjectRole.Status.ELIGIBLE_FOR_JIT),
      "justification",
      new URI("https://example.com/")));

    verify(mailAdapter, times(1)).sendMail(
      eq("recipient@example.com"),
      eq("recipient@example.com"),
      anyString(),
      anyString());
  }

  @Test
  public void whenEmailDisabled_ThenSendNotificationDoesNothing() throws Exception {
    var mailAdapter = Mockito.mock(MailAdapter.class);
    var service = new NotificationService(
      mailAdapter,
      new NotificationService.Options(false));
    service.sendNotification(new NotificationService.ApprovalRequest(
      new UserId("requestor@example.com"),
      new UserId("recipient@example.com"),
      new ProjectRole(
        new RoleBinding("//cloudresourcemanager.googleapis.com/projects/project-1", "project/browser"),
        ProjectRole.Status.ELIGIBLE_FOR_JIT),
      "justification",
      new URI("https://example.com/")));

    verify(mailAdapter, times(0)).sendMail(
      eq("recipient@example.com"),
      eq("recipient@example.com"),
      anyString(),
      anyString());
  }

  // -------------------------------------------------------------------------
  // ApprovalRequest.
  // -------------------------------------------------------------------------

  @Test
  public void whenApprovalRequestContainsHtmlTags_ThenFormatEscapesTags() throws Exception {
    var request = new NotificationService.ApprovalRequest(
      new UserId("<requestor>"),
      new UserId("<recipient>"),
      new ProjectRole(
        new RoleBinding("//cloudresourcemanager.googleapis.com/projects/<resource>", "<role>"),
        ProjectRole.Status.ELIGIBLE_FOR_JIT),
      "<justification>",
      new URI("https://example.com/")).format();

    assertTrue(request.contains("&lt;requestor&gt;"));
    assertTrue(request.contains("&lt;resource&gt;"));
    assertTrue(request.contains("&lt;role&gt;"));
    assertTrue(request.contains("&lt;justification&gt;"));
  }
}
