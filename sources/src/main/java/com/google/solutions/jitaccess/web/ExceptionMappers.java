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

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import com.google.solutions.jitaccess.core.ResourceNotFoundException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.spi.UnhandledException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ExceptionMappers {
  public static final Class<?>[] ALL = new Class<?>[] {
    NotAuthenticatedExceptionMapper.class,
    ResourceNotFoundExceptionMapper.class,
    AccessExceptionMapper.class,
    ForbiddenExceptionMapper.class,
    IllegalArgumentExceptionMapper.class,
    IllegalStateExceptionMapper.class,
    NullPointerExceptionMapper.class,
    IOExceptionMapper.class,
    UnhandledExceptionMapper.class,
    NotAllowedExceptionMapper.class,
    NotAcceptableExceptionMapper.class,
    NotFoundExceptionMapper.class
  };

  @Provider
  public static class NotAuthenticatedExceptionMapper
    implements ExceptionMapper<NotAuthenticatedException> {
    @Override
    public Response toResponse(@NotNull NotAuthenticatedException exception) {
      return Response
        .status(Response.Status.UNAUTHORIZED)
        .entity(new ErrorEntity(exception)).build();
    }
  }

  @Provider
  public static class AccessExceptionMapper
    implements ExceptionMapper<AccessException> {
    @Override
    public Response toResponse(@NotNull AccessException exception) {
      return Response
        .status(Response.Status.FORBIDDEN)
        .entity(new ErrorEntity(exception)).build();
    }
  }

  @Provider
  public static class ResourceNotFoundExceptionMapper
    implements ExceptionMapper<ResourceNotFoundException> {
    @Override
    public Response toResponse(@NotNull ResourceNotFoundException exception) {
      return Response
        .status(Response.Status.NOT_FOUND)
        .entity(new ErrorEntity(exception)).build();
    }
  }

  @Provider
  public static class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {
    @Override
    public Response toResponse(@NotNull ForbiddenException exception) {
      return Response
        .status(Response.Status.FORBIDDEN)
        .entity(new ErrorEntity(exception)).build();
    }
  }

  @Provider
  public static class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    @Override
    public Response toResponse(@NotNull IllegalArgumentException exception) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  @Provider
  public static class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {
    @Override
    public Response toResponse(@NotNull IllegalStateException exception) {
      return Response
        .status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  @Provider
  public static class NullPointerExceptionMapper implements ExceptionMapper<NullPointerException> {
    @Override
    public Response toResponse(@NotNull NullPointerException exception) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  @Provider
  public static class IOExceptionMapper implements ExceptionMapper<IOException> {
    @Override
    public Response toResponse(@NotNull IOException exception) {
      return Response
        .status(Response.Status.BAD_GATEWAY)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  @Provider
  public static class NotAllowedExceptionMapper implements ExceptionMapper<NotAllowedException> {
    @Override
    public Response toResponse(@NotNull NotAllowedException exception) {
      return Response
        .status(Response.Status.METHOD_NOT_ALLOWED)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  @Provider
  public static class NotAcceptableExceptionMapper implements ExceptionMapper<NotAcceptableException> {
    @Override
    public Response toResponse(@NotNull NotAcceptableException exception) {
      return Response
        .status(Response.Status.NOT_ACCEPTABLE)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  @Provider
  public static class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
    @Override
    public Response toResponse(@NotNull NotFoundException exception) {
      return Response
        .status(Response.Status.NOT_FOUND)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  @Provider
  public static class UnhandledExceptionMapper implements ExceptionMapper<UnhandledException> {
    @Override
    public Response toResponse(@NotNull UnhandledException exception) {
      return Response
        .status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(new ErrorEntity(exception))
        .build();
    }
  }

  public static class ErrorEntity {
    private final String message;

    public ErrorEntity(@NotNull Exception exception) {
      this.message = exception.getMessage();
    }

    public String getMessage() {
      return message;
    }
  }
}
