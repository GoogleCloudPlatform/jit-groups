package com.google.solutions.jitaccess.catalog.policy;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class TestAbstractProperty {
  //---------------------------------------------------------------------------
  // Properties.
  //---------------------------------------------------------------------------

  @Test
  public void basicProperties() {
    var property = new AbstractProperty<String>(String.class, "name", "display name", false) {

      @Override
      protected String convertFromString(@Nullable String value) {
        throw new IllegalStateException();
      }

      @Override
      protected String convertToString(@Nullable String value) {
        throw new IllegalStateException();
      }

      @Override
      protected void setCore(@Nullable String value) {
        throw new IllegalStateException();
      }

      @Override
      protected @Nullable String getCore() {
        throw new IllegalStateException();
      }
    };

    assertEquals("name", property.name());
    assertEquals("display name", property.displayName());
    assertEquals(String.class, property.type());
  }

  //---------------------------------------------------------------------------
  // Duration.
  //---------------------------------------------------------------------------

  private static class DurationProperty extends AbstractDurationProperty {
    private @Nullable Duration value;

    public DurationProperty(Duration min, Duration max, boolean isRequired) {
      super("name", "display name", isRequired, min, max);
    }

    @Override
    protected void setCore(@Nullable Duration value) {
      this.value = value;
    }

    @Override
    protected @Nullable Duration getCore() {
      return this.value;
    }
  }

  @Test
  public void duration_whenInvalid_thenSetThrowsException() {
    var property = new DurationProperty(null, null, true);

    assertTrue(property.isRequired());

    assertThrows(
      IllegalArgumentException.class,
      () -> property.set(null));
    assertThrows(
      IllegalArgumentException.class,
      () -> property.set(""));
    assertThrows(
      IllegalArgumentException.class,
      () -> property.set("four"));
  }

  @Test
  public void duration_whenNotRequired() {
    var property = new DurationProperty(null, null, false);

    assertFalse(property.isRequired());
    property.set(null);
    assertNull(property.get());
  }

  @Test
  public void duration_whenOutOfRange_thenSetThrowsException() {
    var property = new DurationProperty(Duration.ofSeconds(1), Duration.ofSeconds(3), true);

    assertThrows(
      IllegalArgumentException.class,
      () -> property.set("0"));
    assertThrows(
      IllegalArgumentException.class,
      () -> property.set("4"));
  }

  @Test
  public void duration_whenInRange_thenSetSucceeds() {
    var property = new DurationProperty(Duration.ofSeconds(1), Duration.ofSeconds(3), true);

    property.set(" PT1S  ");
    assertEquals("PT1S", property.get());

    property.set("PT3S");
    assertEquals("PT3S", property.get());
  }

  @Test
  public void duration_whenRangeUnbounded_thenSetSucceeds() {
    var property = new DurationProperty(null, null, false);

    property.set(" PT0S  ");
    assertEquals("PT0S", property.get());

    property.set("P4DT12H30M5S");
    assertEquals("PT108H30M5S", property.get());
  }
  
  //---------------------------------------------------------------------------
  // Integer.
  //---------------------------------------------------------------------------

  private static class LongProperty extends AbstractLongProperty {
    private @Nullable Long value;

    public LongProperty(Long min, Long max, boolean isRequired) {
      super("name", "display name", isRequired, min, max);
    }

    @Override
    protected void setCore(@Nullable Long value) {
      this.value = value;
    }

    @Override
    protected @Nullable Long getCore() {
      return this.value;
    }
  }

  @Test
  public void long_whenInvalid_thenSetThrowsException() {
    var property = new LongProperty(null, null, true);

    assertTrue(property.isRequired());

    assertThrows(
      IllegalArgumentException.class,
      () -> property.set(null));
    assertThrows(
      IllegalArgumentException.class,
      () -> property.set(""));
    assertThrows(
      IllegalArgumentException.class,
      () -> property.set("four"));
  }

  @Test
  public void long_whenNotRequired() {
    var property = new LongProperty(null, null, false);

    assertFalse(property.isRequired());
    property.set(null);
    assertNull(property.get());
  }

  @Test
  public void long_whenOutOfRange_thenSetThrowsException() {
    var property = new LongProperty(1L, 3L, true);

    assertThrows(
      IllegalArgumentException.class,
      () -> property.set("0"));
    assertThrows(
      IllegalArgumentException.class,
      () -> property.set("4"));
  }

  @Test
  public void long_whenInRange_thenSetSucceeds() {
    var property = new LongProperty(1L, 3L, true);

    property.set(" 1  ");
    assertEquals("1", property.get());

    property.set("3");
    assertEquals("3", property.get());
  }

  @Test
  public void long_whenRangeUnbounded_thenSetSucceeds() {
    var property = new LongProperty(null, null, true);

    property.set(String.valueOf(Integer.MIN_VALUE));
    assertEquals(String.valueOf(Integer.MIN_VALUE), property.get());

    property.set(String.valueOf(Integer.MAX_VALUE));
    assertEquals(String.valueOf(Integer.MAX_VALUE), property.get());
  }

  //---------------------------------------------------------------------------
  // Boolean.
  //---------------------------------------------------------------------------

  private static class BooleanProperty extends AbstractBooleanProperty {
    private @Nullable Boolean value;

    public BooleanProperty(boolean isRequired) {
      super("name", "display name", isRequired);
    }

    @Override
    protected void setCore(@Nullable Boolean value) {
      this.value = value;
    }

    @Override
    protected @Nullable Boolean getCore() {
      return this.value;
    }
  }


  @Test
  public void boolean_whenInvalid_thenSetThrowsException() {
    var property = new BooleanProperty(true);

    assertTrue(property.isRequired());

    assertThrows(
      IllegalArgumentException.class,
      () -> property.set(null));
  }

  @Test
  public void boolean_whenInRange_thenSetSucceeds() {
    var property = new BooleanProperty(true);

    property.set(" TRUE");
    assertEquals("true", property.get());

    property.set(" False");
    assertEquals("false", property.get());

    property.set(" ");
    assertEquals("false", property.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"True", " yes ", " ON\n"})
  public void boolean_whenTruthy(String value) {
    var property = new BooleanProperty(true);

    property.set(value);
    assertEquals("true", property.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Maybe", " FALSE ", " Off"})
  public void boolean_whenNotTruthy(String value) {
    var property = new BooleanProperty(true);

    property.set(value);
    assertEquals("false", property.get());
  }

  @Test
  public void boolean_whenNotRequired() {
    var property = new BooleanProperty(false);

    assertFalse(property.isRequired());
    property.set(null);
    assertNull(property.get());
  }

  //---------------------------------------------------------------------------
  // String.
  //---------------------------------------------------------------------------

  private static class StringProperty extends AbstractStringProperty {
    private @Nullable String value;

    public StringProperty(boolean isRequired) {
      super("name", "display name", isRequired, 2, 5);
    }

    @Override
    protected void setCore(@Nullable String value) {
      this.value = value;
    }

    @Override
    protected @Nullable String getCore() {
      return this.value;
    }
  }

  @Test
  public void string_whenInvalid_thenSetThrowsException() {
    var property = new StringProperty(true);

    assertTrue(property.isRequired());

    assertThrows(
      IllegalArgumentException.class,
      () -> property.set(null));
  }

  @Test
  public void string_whenNotRequired() {
    var property = new StringProperty(false);

    assertFalse(property.isRequired());
    property.set(null);
    assertNull(property.get());
  }

  @Test
  public void string_whenOutOfRange_thenSetThrowsException() {
    var property = new StringProperty(true);

    assertThrows(
      IllegalArgumentException.class,
      () -> property.set("a"));
    assertThrows(
      IllegalArgumentException.class,
      () -> property.set("aaaaaa"));
  }

  @Test
  public void string_whenInRange_thenSetSucceeds() {
    var property = new StringProperty(true);

    property.set(" test     ");
    assertEquals("test", property.get());
  }
}
