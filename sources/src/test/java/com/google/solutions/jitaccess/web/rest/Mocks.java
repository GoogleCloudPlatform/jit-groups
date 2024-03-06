package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

abstract class Mocks {

  static NotificationService createNotificationServiceMock(boolean canSend) {
    var service = Mockito.mock(NotificationService.class);
    when(service.canSendNotifications()).thenReturn(canSend);
    return service;
  }

  static MpaProjectRoleCatalog createMpaProjectRoleCatalogMock() {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    return catalog;
  }

  static UriInfo createUriInfoMock() {
    return new ResteasyUriInfo("http://example.com/", "/");
  }

  static RuntimeEnvironment createRuntimeEnvironmentMock() {
    var environment = Mockito.mock(RuntimeEnvironment.class);
    when(environment.createAbsoluteUriBuilder(any(UriInfo.class)))
      .thenReturn(UriBuilder.fromUri("https://example.com/"));
    return environment;
  }
}
