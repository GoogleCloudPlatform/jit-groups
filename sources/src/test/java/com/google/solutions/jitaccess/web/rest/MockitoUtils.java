package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.notifications.NotificationService;
import jakarta.enterprise.inject.Instance;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.Mockito.when;

class MockitoUtils {
  /**
   * Create an Instance for a given object.
   */
  static <T> Instance<T> toCdiInstance(T obj) {
    var instance = Mockito.mock(Instance.class);
    when(instance.stream()).thenReturn(List.of(obj).stream());
    when(instance.iterator()).thenReturn(List.of(obj).iterator());

    return instance;
  }
}
