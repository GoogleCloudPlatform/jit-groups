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

package com.google.solutions.jitaccess.core.adapters;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Factory for creating transports based on the 'javax.net.ssl.trustStore'
 * system property.
 */
public class HttpTransport {
  private HttpTransport() {}

  public static NetHttpTransport newTransport() throws GeneralSecurityException, IOException {
    var trustStore = System.getProperty("javax.net.ssl.trustStore");
    var trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");

    if (trustStore != null && trustStorePassword != null) {
      //
      // Use a custom keystore.
      //
      // NB. There's little reason to use a custom trust store in production, but
      // during development, using a custom trust store is necessary if we want to
      // trace and decrypt HTTPS traffic.
      //
      try (var trustStoreStream = new FileInputStream(trustStore)) {
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(trustStoreStream, trustStorePassword.toCharArray());

        return new NetHttpTransport
          .Builder()
          .trustCertificates(keyStore)
          .build();
      }
    }
    else {
      //
      // Use the Google keystore.
      //
      return GoogleNetHttpTransport.newTrustedTransport();
    }
  }
}
