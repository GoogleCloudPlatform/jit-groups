//
// Copyright 2022 Google LLC
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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class RuntimeConfiguration {
  private final Function<String, String> readSetting;

  public RuntimeConfiguration(Map<String, String> settings) {
    this(key -> settings.get(key));
  }

  public RuntimeConfiguration(Function<String, String> readSetting) {
    this.readSetting = readSetting;

    this.scope = new StringSetting(
      List.of("RESOURCE_SCOPE"),
      String.format("projects/%s", this.readSetting.apply("GOOGLE_CLOUD_PROJECT")));

    //
    // Activation settings.
    //
    this.activationTimeout = new DurationSetting(
      List.of("ELEVATION_DURATION", "ACTIVATION_TIMEOUT"),
      Duration.ofHours(2));
    this.activationRequestTimeout = new DurationSetting(
      List.of("ACTIVATION_REQUEST_TIMEOUT"),
      Duration.ofHours(1));
    this.justificationPattern = new StringSetting(
      List.of("JUSTIFICATION_PATTERN"),
      ".*");
    this.justificationHint = new StringSetting(
      List.of("JUSTIFICATION_HINT"),
      "Bug or case number");

    //
    // Mail settings.
    //
    new StringSetting(
      List.of("SMTP_HOST"),
      "smtp.gmail.com");
    new IntSetting(
      List.of("SMTP_PORT"),
      587);
    new StringSetting(
      List.of("SMTP_SENDER_NAME"),
      "JIT Access");
    new StringSetting(
      List.of("SMTP_SENDER_ADDRESS"),
      null);

    new StringSetting(List.of("SMTP_USERNAME"), null);
    new StringSetting(List.of("SMTP_PASSWORD"), null);
  }

  // -------------------------------------------------------------------------
  // Settings.
  // -------------------------------------------------------------------------

  /**
   * Scope (within the resource hierarchy) that this application manages
   * access for.
   */
  public final StringSetting scope;

  /**
   * Duration for which an activated role remains activated.
   */
  public final DurationSetting activationTimeout;

  /**
   * Time allotted for reviewers to approve an activation request.
   */
  public final DurationSetting activationRequestTimeout;

  /**
   * Regular expression that justifications must satisfy.
   */
  public final StringSetting justificationPattern;

  /**
   * Hint (or description) for users indicating what kind of justification they
   * need to supply.
   */
  public final StringSetting justificationHint;

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public abstract class Setting {
    public final Collection<String> keys;

    protected <T> T colaesceValues(T value, T defaultValue) {
      if (value != null) {
        return value;
      }
      else if (defaultValue != null) {
        return defaultValue;
      }
      else {
        throw new IllegalStateException("No value provided for " + this.keys.toString());
      }
    }

    protected String getValue() {
      for (var key : this.keys) {
        var value = readSetting.apply(key);
        if (value != null) {
          value = value.trim();
          if (!value.isEmpty()) {
            return value;
          }
        }
      }

      return null;
    }

    public Setting(Collection<String> keys) {
      this.keys = keys;
    }
  }

  public class StringSetting extends Setting {
    private final String defaultValue;

    public StringSetting(Collection<String> keys, String defaultValue) {
      super(keys);
      this.defaultValue = defaultValue;
    }

    public String getString() {
      return colaesceValues(getValue(), this.defaultValue);
    }
  }

  public class IntSetting extends Setting {
    private final Integer defaultValue;

    public IntSetting(Collection<String> keys, Integer defaultValue) {
      super(keys);
      this.defaultValue = defaultValue;
    }

    public int getString() {
      return colaesceValues(Integer.parseInt(getValue()), this.defaultValue).intValue();
    }
  }

  public class DurationSetting extends Setting {
    private final Duration defaultValue;

    public DurationSetting(Collection<String> keys, Duration defaultValue) {
      super(keys);
      this.defaultValue = defaultValue;
    }

    public Duration getDuration() {
      var value = getValue();
      return (value != null)
        ? Duration.ofMinutes(Integer.parseInt(value))
        : this.defaultValue;
    }
  }
}
