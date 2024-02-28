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

package com.google.solutions.jitaccess.cel;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelRuntime;

import java.util.List;

/**
 * Extract function as documented in
 * https://cloud.google.com/iam/docs/conditions-attribute-reference#extract.
 */
public class ExtractFunction  {
  public static final CelFunctionDecl DECLARATION =
    CelFunctionDecl.newFunctionDeclaration(
      "extract",
      CelOverloadDecl.newMemberOverload(
        "extract_string_string",
        SimpleType.STRING,
        List.of(SimpleType.STRING, SimpleType.STRING)));

  public static final CelRuntime.CelFunctionBinding BINDING =
    CelRuntime.CelFunctionBinding.from(
      "extract_string_string",
      String.class,
      String.class,
      ExtractFunction::execute
    );

  static String execute(String value, String template) {
    var openingBraceIndex = template.indexOf('{');
    var closingBraceIndex = template.indexOf('}');

    if (openingBraceIndex < 0 || closingBraceIndex < 0) {
      return value;
    }

    var prefix = template.substring(0, openingBraceIndex);
    var suffix = closingBraceIndex == template.length() - 1
      ? ""
      : template.substring(closingBraceIndex + 1);

    if (value.contains(prefix)) {
      var afterPrefix = value.substring(value.indexOf(prefix) + prefix.length());
      if (suffix.length() == 0) {
        return afterPrefix;
      }
      else if (afterPrefix.contains(suffix)) {
        return afterPrefix.substring(0, afterPrefix.indexOf(suffix));
      }
    }

    return "";
  }
}
