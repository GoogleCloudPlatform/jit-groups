//
// Copyright 2023 Google LLC
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

import java.net.HttpURLConnection;

import com.google.common.base.Preconditions;
import com.slack.api.Slack;
import com.slack.api.webhook.Payload;

public class SlackAdapter {
  private final Slack slack;
  private final Options options;

  public SlackAdapter(Options options) {
    Preconditions.checkNotNull(options, "options");
    this.options = options;
    this.slack = Slack.getInstance();
  }

  public void sendSlackMessage(String content) throws SlackException {
    Preconditions.checkNotNull(content, "content");

    try {
      var response = slack.send(this.options.slackHookUrl, Payload.builder().text(content).build());
      if (!response.getCode().equals(HttpURLConnection.HTTP_OK)) {
        throw new SlackException(
          "The Slack notification could not be sent. HTTP Code: " + response.getCode(), null);
      }
    } catch (Exception e) {
      throw new SlackException("The Slack notification could not be sent", e);
    }
  }

  public static class Options {
    private final String slackHookUrl;

    public Options(String slackHookUrl) {
      Preconditions.checkNotNull(slackHookUrl, "slackHookUrl");

      this.slackHookUrl = slackHookUrl;
    }
  }

  public static class SlackException extends Exception {
    public SlackException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
