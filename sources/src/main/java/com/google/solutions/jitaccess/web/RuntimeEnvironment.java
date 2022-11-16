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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.solutions.jitaccess.core.ApplicationVersion;
import com.google.solutions.jitaccess.core.adapters.*;
import com.google.solutions.jitaccess.core.services.RoleActivationService;
import com.google.solutions.jitaccess.core.services.RoleDiscoveryService;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides access to runtime configuration (AppEngine, local). To be injected using CDI.
 */
@ApplicationScoped
public class RuntimeEnvironment {
  private static final String CONFIG_IMPERSONATE_SA = "jitaccess.impersonateServiceAccount";
  private static final String CONFIG_STATIC_PRINCIPAL = "jitaccess.principal";

  private static final Logger LOG = Logger.getLogger(RuntimeEnvironment.class);

  private final String ProjectId;
  private final String ProjectNumber;
  private final UserPrincipal StaticPrincipal;
  private final String ApplicationPrincipal;
  private final GoogleCredentials ApplicationCredentials;
  private final RoleDiscoveryService.Options roleDiscoveryServiceOptions;
  private final RoleActivationService.Options roleActivationServiceOptions;

  private static HttpResponse getMetadata(String path) throws IOException {
    GenericUrl genericUrl = new GenericUrl(ComputeEngineCredentials.getMetadataServerUrl() + path);
    HttpRequest request = new NetHttpTransport().createRequestFactory().buildGetRequest(genericUrl);

    request.setParser(new JsonObjectParser(GsonFactory.getDefaultInstance()));
    request.getHeaders().set("Metadata-Flavor", "Google");
    request.setThrowExceptionOnExecuteError(true);

    try {
      return request.execute();
    }
    catch (UnknownHostException exception) {
      throw new IOException(
        "Cannot find the metadata server. This is "
          + "likely because code is not running on Google Cloud.",
        exception);
    }
  }

  private static String getConfigurationOption(String key, String defaultValue) {
    var value = System.getenv(key);
    if (value != null && !value.isEmpty()) {
      return value.trim();
    }
    else if (defaultValue != null) {
      return defaultValue;
    }
    else {
      throw new RuntimeException(String.format("Missing configuration '%s'", key));
    }
  }

  public RuntimeEnvironment() {
    if (System.getenv().containsKey("GAE_SERVICE")) {
      //
      // Running on AppEngine.
      //
      try {
        GenericData projectMetadata =
          getMetadata("/computeMetadata/v1/project/?recursive=true").parseAs(GenericData.class);

        this.ProjectId = (String) projectMetadata.get("projectId");
        this.ProjectNumber = projectMetadata.get("numericProjectId").toString();
        this.StaticPrincipal = null; // Use proper IAP authentication.

        this.ApplicationCredentials = GoogleCredentials.getApplicationDefault();
        this.ApplicationPrincipal =
          ((ComputeEngineCredentials) this.ApplicationCredentials).getAccount();

        LOG.infof(
          "Running in project %s (%s) as %s, version %s",
          this.ProjectId,
          this.ProjectNumber,
          this.ApplicationPrincipal,
          ApplicationVersion.VERSION_STRING);
      }
      catch (IOException e) {
        LOG.errorf(e, "Failed to lookup instance metadata");
        throw new RuntimeException("Failed to initialize runtime environment", e);
      }
    }
    else {
      //
      // Running in development mode.
      //
      this.ProjectId = "dev";
      this.ProjectNumber = "0";
      this.StaticPrincipal = new UserPrincipal() {
        @Override
        public UserId getId() {
          return new UserId(getName(), getName());
        }

        @Override
        public DeviceInfo getDevice() {
          return DeviceInfo.UNKNOWN;
        }

        @Override
        public String getName() {
          return System.getProperty(CONFIG_STATIC_PRINCIPAL, "developer@example.com");
        }
      };

      try {
        GoogleCredentials defaultCredentials = GoogleCredentials.getApplicationDefault();

        String impersonateServiceAccount = System.getProperty(CONFIG_IMPERSONATE_SA);
        if (impersonateServiceAccount != null && !impersonateServiceAccount.isEmpty()) {
          //
          // Use the application default credentials (ADC) to impersonate a
          // service account. This can be used when using user credentials as ADC.
          //
          this.ApplicationCredentials =
            ImpersonatedCredentials.create(
              defaultCredentials,
              impersonateServiceAccount,
              null,
              Stream.of(ResourceManagerAdapter.OAUTH_SCOPE, AssetInventoryAdapter.OAUTH_SCOPE)
                .distinct()
                .collect(Collectors.toList()),
              0);

          //
          // If we lack impersonation permissions, ImpersonatedCredentials
          // will keep retrying until the call timeout expires. The effect
          // is that the application seems hung.
          //
          // To prevent this from happening, force a refresh here. If the
          // refresh fails, fail application startup.
          //
          this.ApplicationCredentials.refresh();
          this.ApplicationPrincipal = impersonateServiceAccount;
        }
        else if (defaultCredentials instanceof ServiceAccountCredentials) {
          //
          // Use ADC as-is.
          //
          this.ApplicationCredentials = defaultCredentials;
          this.ApplicationPrincipal =
            ((ServiceAccountCredentials) this.ApplicationCredentials).getServiceAccountUser();
        }
        else {
          throw new RuntimeException(
            String.format(
              "You're using user credentials as application default "
                + "credentials (ADC). Use -D%s=<service-account-email> to impersonate "
                + "a service account during development",
              CONFIG_IMPERSONATE_SA));
        }
      }
      catch (IOException e) {
        throw new RuntimeException("Failed to lookup application credentials", e);
      }

      LOG.warnf("Running in development mode as %s", this.ApplicationPrincipal);
    }

    this.roleDiscoveryServiceOptions =
      new RoleDiscoveryService.Options(
        getConfigurationOption(
          "RESOURCE_SCOPE",
          "projects/" + getConfigurationOption("GOOGLE_CLOUD_PROJECT", null)),
        Boolean.parseBoolean(getConfigurationOption("INCLUDE_INHERITED_BINDINGS", "false")));

    this.roleActivationServiceOptions =
      new RoleActivationService.Options(
        getConfigurationOption("JUSTIFICATION_HINT", "Bug or case number"),
        Pattern.compile(getConfigurationOption("JUSTIFICATION_PATTERN", ".*")),
        Duration.ofMinutes(
          Integer.parseInt(getConfigurationOption("ELEVATION_DURATION", "5"))));
  }

  public String getProjectId() {
    return ProjectId;
  }

  public String getProjectNumber() {
    return ProjectNumber;
  }

  public UserPrincipal getStaticPrincipal() {
    return StaticPrincipal;
  }

  public String getApplicationPrincipal() {
    return ApplicationPrincipal;
  }

  @Produces // Make available for injection into adapter classes
  public GoogleCredentials getApplicationCredentials() {
    return ApplicationCredentials;
  }

  @Produces // Make available for injection into adapter classes
  public RoleDiscoveryService.Options getRoleDiscoveryServiceOptions() {
    return this.roleDiscoveryServiceOptions;
  }

  @Produces // Make available for injection into adapter classes
  public RoleActivationService.Options getRoleActivationServiceOptions() {
    return this.roleActivationServiceOptions;
  }
}
