//
// Copyright 2021 Google LLC
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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import org.jboss.resteasy.spi.UnhandledException;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

public class ExceptionMappers {
  @Provider
  public static class NotAuthenticatedExceptionMapper
    implements ExceptionMapper<NotAuthenticatedException> {
    @Override
    public Response toResponse(NotAuthenticatedException exception) {
      return Response
        .status(Response.Status.UNAUTHORIZED)
        .entity(new ErrorEntity(exception)).build();
    }
  }

  @Provider
  public static class AccessDeniedExceptionExceptionMapper
    implements ExceptionMapper<AccessDeniedException> {
    @Override
    public Response toResponse(AccessDeniedException exception) {
      return Response
        .status(Response.Status.FORBIDDEN)
        .entity(new ErrorEntity(exception)).build();
    }
  }

  @Provider
  public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {
    @Override
    public Response toResponse(ForbiddenException exception) {
      return Response
        .status(Response.Status.FORBIDDEN)
        .entity(new ErrorEntity(exception)).build();
    }
  }

  @Provider
  public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    @Override
    public Response toResponse(IllegalArgumentException exception) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  @Provider
  public class IOExceptionMapper implements ExceptionMapper<IOException> {
    @Override
    public Response toResponse(IOException exception) {
      return Response
        .status(Response.Status.BAD_GATEWAY)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  @Provider
  public class UnhandledExceptionMapper implements ExceptionMapper<UnhandledException> {
    @Override
    public Response toResponse(UnhandledException exception) {
      return Response
        .status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  public static class ErrorEntity {
    private final String message;

    public ErrorEntity(Exception exception) {
      this.message = exception.getMessage();
    }

    public String getMessage() {
      return message;
    }
  }
}
