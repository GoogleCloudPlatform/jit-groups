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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Base implementation of a property.
 */
abstract class AbstractProperty<T> implements Property {
  private final  @NotNull Class<T> type;
  private final @NotNull String name;
  private final @NotNull String displayName;
  private final boolean isRequired;

  protected final @Nullable T minInclusive;
  protected final @Nullable T maxInclusive;

  protected AbstractProperty(
    @NotNull Class<T> type,
    @NotNull String name,
    @NotNull String displayName,
    boolean isRequired,
    @Nullable T minInclusive,
    @Nullable T maxInclusive
  ) {
    this.type = type;
    this.name = name;
    this.displayName = displayName;
    this.isRequired = isRequired;
    this.minInclusive = minInclusive;
    this.maxInclusive = maxInclusive;
  }

  protected AbstractProperty(
    @NotNull Class<T> type,
    @NotNull String name,
    @NotNull String displayName,
    boolean isRequired
  ) {
    this(type, name, displayName, isRequired, null, null);
  }

  /**
   * Unique name of the property.
   */
  @Override
  public @NotNull String name() {
    return name;
  }

  /**
   * Display name of the property.
   */
  @Override
  public @NotNull String displayName() {
    return displayName;
  }

  /**
   * Type of the property.
   */
  @Override
  public @NotNull Class<?> type() {
    return this.type;
  }

  /**
   * Indicates whether a non-null value is required.
   */
  @Override
  public boolean isRequired() {
    return isRequired;
  }

  /**
   * Convert string representation to the typed representation.
   *
   * @throws IllegalArgumentException when a conversion is not possible.
   */
  protected abstract @Nullable T convertFromString(@Nullable String value);

  /**
   * Convert to string representation.
   *
   * @throws IllegalArgumentException when a conversion is not possible.
   */
  protected abstract @Nullable String convertToString(@Nullable T value);

  /**
   * Validate the value.
   *
   * @throws IllegalArgumentException if the value is invalid.
   */
  protected void validateRange(@Nullable T value) {
    Preconditions.checkArgument(
      !this.isRequired || value != null,
      String.format("No value provided for '%s'", this.displayName));
  }

  /**
   * Assign the (validated) new value to a backing store.
   */
  protected abstract void setCore(@Nullable T value);

  /**
   * Read the assigned value from a backing store.
   */
  protected abstract @Nullable T getCore();

  /**
   * Minimum allowed value, if constrained.
   */
  @Override
  public @NotNull Optional<String> minInclusive() {
    return Optional.ofNullable(convertToString(this.minInclusive));
  }

  /**
   * Maximum allowed value, if constrained.
   */
  @Override
  public @NotNull Optional<String> maxInclusive() {
    return Optional.ofNullable(convertToString(this.maxInclusive));
  }

  /**
   * Parse string value and assign to property.
   */
  @Override
  public final void set(@Nullable String s) {
    T value;
    try {
      value = convertFromString(s);
    }
    catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
        String.format("The value for '%s' is invalid", this.displayName()));
    }

    validateRange(value);
    setCore(value);
  }

  /**
   * Read assigned value, converted to a string.
   */
  @Override
  public @Nullable String get() {
    var value = getCore();
    return value == null ? null : convertToString(value);
  }
}

/**
 * A Duration-typed property.
 */
abstract class AbstractDurationProperty extends AbstractProperty<Duration> {
  protected AbstractDurationProperty(
    @NotNull String name,
    @NotNull String displayName,
    boolean isRequired,
    @Nullable Duration minInclusive,
    @Nullable Duration maxInclusive)
  {
    super(Duration.class, name, displayName, isRequired, minInclusive, maxInclusive);
  }

  @Override
  protected void validateRange(@Nullable Duration value) {
    super.validateRange(value);

    if (value != null && this.minInclusive != null && value.compareTo(this.minInclusive) < 0) {
      throw new IllegalArgumentException(
        String.format("The value for '%s' is too small", this.displayName()));
    }

    if (value != null && this.maxInclusive != null && value.compareTo(this.maxInclusive) > 0) {
      throw new IllegalArgumentException(
        String.format("The value for '%s' is too large", this.displayName()));
    }
  }

  @Override
  protected @Nullable Duration convertFromString(@Nullable String value) {
    try {
      return value != null ? Duration.parse(value.trim()) : null;
    }
    catch (DateTimeParseException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  @Override
  protected @Nullable String convertToString(@Nullable Duration value) {
    return value != null ? value.toString() : null;
  }
}

/**
 * A long-typed property.
 */
abstract class AbstractLongProperty extends AbstractProperty<Long> {
  protected AbstractLongProperty(
    @NotNull String name,
    @NotNull String displayName,
    boolean isRequired,
    @Nullable Long minInclusive,
    @Nullable Long maxInclusive)
  {
    super(Long.class, name, displayName, isRequired, minInclusive, maxInclusive);
  }

  @Override
  protected void validateRange(@Nullable Long value) {
    super.validateRange(value);

    if (value != null && this.minInclusive != null && value < this.minInclusive) {
      throw new IllegalArgumentException(
        String.format("The value for '%s' is too small", this.name()));
    }

    if (value != null && this.maxInclusive != null && value > this.maxInclusive) {
      throw new IllegalArgumentException(
        String.format("The value for '%s' is too large", this.name()));
    }
  }

  @Override
  protected @Nullable Long convertFromString(@Nullable String value) {
    return value != null ? Long.parseLong(value.trim()) : null;
  }

  @Override
  protected @Nullable String convertToString(@Nullable Long value) {
    return value != null ? String.valueOf(value) : null;
  }
}

/**
 * A Boolean-typed property.
 */
abstract class AbstractBooleanProperty extends AbstractProperty<Boolean> {
  protected AbstractBooleanProperty(
    @NotNull String name,
    @NotNull String displayName,
    boolean isRequired)
  {
    super(Boolean.class, name, displayName, isRequired);
  }

  @Override
  protected @Nullable Boolean convertFromString(@Nullable String value) {
    if (value == null) {
      return null;
    }

    return switch (value.trim().toLowerCase()) {
      case "true", "on", "yes" -> true;
      default -> false;
    };
  }

  @Override
  protected @Nullable String convertToString(@Nullable Boolean value) {
    return value != null ? value.toString() : null;
  }
}

/**
 * A String-typed property.
 *
 * Minimum and maximum are interpreted as minimum and maximum
 * string lengths, not values.
 */
abstract class AbstractStringProperty extends AbstractProperty<String> {
  private final int minLength;
  private final int maxLength;
  protected AbstractStringProperty(
    @NotNull String name,
    @NotNull String displayName,
    boolean isRequired,
    int minLength,
    int maxLength)
  {
    super(
      String.class,
      name,
      displayName,
      isRequired,
      String.valueOf(minLength),
      String.valueOf(maxLength));

    this.minLength = minLength;
    this.maxLength = maxLength;
  }

  @Override
  protected void validateRange(@Nullable String value) {
    super.validateRange(value);

    if (value != null && value.length() < this.minLength) {
      throw new IllegalArgumentException(
        String.format("The value for '%s' is too short", this.displayName()));
    }

    if (value != null && value.length() > this.maxLength) {
      throw new IllegalArgumentException(
        String.format("The value for '%s' is too long", this.displayName()));
    }
  }

  @Override
  protected @Nullable String convertFromString(@Nullable String value) {
    return value != null ? value.trim() : null;
  }

  @Override
  protected @Nullable String convertToString(@Nullable String value) {
    return value;
  }
}