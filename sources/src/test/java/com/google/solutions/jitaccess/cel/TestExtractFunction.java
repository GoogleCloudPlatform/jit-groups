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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestExtractFunction {

  //-------------------------------------------------------------------------
  // evaluate: extract().
  //-------------------------------------------------------------------------

  @Test
  public void extract() throws Exception {
    var value = "projects/_/buckets/acme-orders-aaa/objects/data_lake/orders/order_date=2019-11-03/aef87g87ae0876";

    assertEquals(
      "2019-11-03",
      ExtractFunction.execute(value, "/order_date={date}/"));
    assertEquals(
      "acme-orders-aaa",
      ExtractFunction.execute(value, "buckets/{name}/"));
    assertEquals(
      "",
      ExtractFunction.execute(value, "/orders/{empty}order_date"));
    assertEquals(
      "projects/_/buckets/acme-orders-aaa",
      ExtractFunction.execute(value, "{start}/objects/data_lake"));
    assertEquals(
      "order_date=2019-11-03/aef87g87ae0876",
      ExtractFunction.execute(value, "orders/{end}"));
    assertEquals(
      value,
      ExtractFunction.execute(value, "{all}"));
    assertEquals(
      "",
      ExtractFunction.execute(value, "/orders/{none}/order_date="));
    assertEquals(
      "",
      ExtractFunction.execute(value, "/orders/order_date=2019-11-03/{id}/data_lake"));
  }
}
