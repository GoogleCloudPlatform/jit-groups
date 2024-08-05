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

package com.google.solutions.jitaccess.apis.clients;

import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.catalog.auth.EmailAddress;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Properties;

public class AppEngineMailClient extends MailClient {
  private final @NotNull String senderName;
  private final @NotNull EmailAddress senderAddress;

  public AppEngineMailClient(
    @NotNull String senderName,
    @NotNull EmailAddress senderAddress
  ) {
    this.senderName = senderName;
    this.senderAddress = senderAddress;
  }

  @Override
  protected InternetAddress senderAddress() throws MailException, IOException {
    return new InternetAddress(
      this.senderAddress.value(),
      this.senderName);
  }

  @Override
  protected Session createSession() {
    //
    // Use defaults from appengine-api JAR.
    //
    return Session.getDefaultInstance(new Properties(), null);
  }
}
