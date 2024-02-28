//
// Copyright 2024 Google LLC
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

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.apis.clients.NotAuthenticatedException;
import com.google.solutions.jitaccess.apis.clients.ResourceNotFoundException;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestExceptionMapper {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");

  @Path("/api/")
  public static class Resource {
    @GET
    @Path("get")
    @Produces(MediaType.APPLICATION_JSON)
    public void get() {
    }

    @GET
    @Path("io-exception")
    @Produces(MediaType.APPLICATION_JSON)
    public void throwIoException() throws IOException {
      throw new IOException("mock");
    }

    @GET
    @Path("auth-exception")
    @Produces(MediaType.APPLICATION_JSON)
    public void throwNotAuthenticatedException() throws NotAuthenticatedException {
      throw new NotAuthenticatedException("mock", new Exception());
    }

    @GET
    @Path("access-exception")
    @Produces(MediaType.APPLICATION_JSON)
    public void throwAccessException() throws AccessException {
      throw new AccessDeniedException("mock");
    }

    @GET
    @Path("notfound-exception")
    @Produces(MediaType.APPLICATION_JSON)
    public void throwNotFoundException() throws NotFoundException {
      throw new NotFoundException("mock");
    }

    @GET
    @Path("resourcenotfound-exception")
    @Produces(MediaType.APPLICATION_JSON)
    public void throwResourceNotFoundException() throws ResourceNotFoundException {
      throw new ResourceNotFoundException("mock");
    }

    @GET
    @Path("argument-exception")
    @Produces(MediaType.APPLICATION_JSON)
    public void throwIllegalArgumentException() {
      throw new IllegalArgumentException("mock");
    }

    @GET
    @Path("argument-exception-as-text")
    @Produces(MediaType.TEXT_PLAIN)
    public void throwIllegalArgumentExceptionAsText() {
      throw new IllegalArgumentException("mock");
    }
  }

  @Test
  public void get_whenPathNotMapped() throws Exception {
    var response = new RestDispatcher<>(new Resource(), SAMPLE_USER)
      .get("/api/unknown", ExceptionMappers.ErrorEntity.class);

    assertEquals(404, response.getStatus());
  }

  @Test
  public void get_whenMethodNotMapped_ThenPostReturnsError() throws Exception {
    var response = new RestDispatcher<>(new Resource(), SAMPLE_USER)
      .post("/api/get", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void get_whenMappedResourceThrowsIOException() throws Exception {
    var response = new RestDispatcher<>(new Resource(), SAMPLE_USER)
      .get("/api/io-exception", ExceptionMappers.ErrorEntity.class);

    assertEquals(502, response.getStatus());

    var body = response.getBody();
    assertEquals("mock", body.getMessage());
  }

  @Test
  public void get_whenMappedResourceThrowsAuthenticationException() throws Exception {
    var response = new RestDispatcher<>(new Resource(), SAMPLE_USER)
      .get("/api/auth-exception", ExceptionMappers.ErrorEntity.class);

    assertEquals(401, response.getStatus());

    var body = response.getBody();
    assertEquals("mock", body.getMessage());
  }

  @Test
  public void get_whenMappedResourceThrowsAccessException() throws Exception {
    var response = new RestDispatcher<>(new Resource(), SAMPLE_USER)
      .get("/api/access-exception", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertEquals("mock", body.getMessage());
  }

  @Test
  public void get_whenMappedResourceThrowsArgumentException() throws Exception {
    var response = new RestDispatcher<>(new Resource(), SAMPLE_USER)
      .get("/api/argument-exception", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertEquals("mock", body.getMessage());
  }

  @Test
  public void get_whenMappedResourceThrowsArgumentExceptionAsText() throws Exception {
    var response = new RestDispatcher<>(new Resource(), SAMPLE_USER)
      .get("/api/argument-exception-as-text", String.class);

    assertEquals(400, response.getStatus());
    assertEquals("mock", response.getBody());
  }

  @Test
  public void get_whenMappedResourceThrowsNotFoundException() throws Exception {
    var response = new RestDispatcher<>(new Resource(), SAMPLE_USER)
      .get("/api/notfound-exception", ExceptionMappers.ErrorEntity.class);

    assertEquals(404, response.getStatus());

    var body = response.getBody();
    assertEquals("mock", body.getMessage());
  }

  @Test
  public void get_whenMappedResourceThrowsResourceNotFoundException() throws Exception {
    var response = new RestDispatcher<>(new Resource(), SAMPLE_USER)
      .get("/api/resourcenotfound-exception", ExceptionMappers.ErrorEntity.class);

    assertEquals(404, response.getStatus());

    var body = response.getBody();
    assertEquals("mock", body.getMessage());
  }
}
