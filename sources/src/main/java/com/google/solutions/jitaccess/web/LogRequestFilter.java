package com.google.solutions.jitaccess.web;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Dependent
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
@LogRequest
public class LogRequestFilter implements ContainerRequestFilter {
  /**
   * Header that contains a unique identifier for the request, cf.
   * https://cloud.google.com/appengine/docs/standard/java11/reference/request-response-headers
   */
  private static final String TRACE_CONTEXT_HEADER_NAME = "X-Cloud-Trace-Context";

  @Inject
  RequestContext requestContext;

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    this.requestContext.initialize(
      containerRequestContext.getRequest().getMethod(),
      containerRequestContext.getUriInfo().getPath(),
      containerRequestContext.getHeaderString(TRACE_CONTEXT_HEADER_NAME));
  }
}
