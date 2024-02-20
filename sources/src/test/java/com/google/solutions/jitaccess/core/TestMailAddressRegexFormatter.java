package com.google.solutions.jitaccess.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.solutions.jitaccess.core.MailAddressRegexFormatter.Options;

public class TestMailAddressRegexFormatter {
    @Test
    public void whenInternalTransformCapturesRawAddress_ThenFormatReturnsRawAddress() {
        var internalPattern = Pattern.compile("(.*)");
        var internalTransform = "%s";
        var externalPattern = (Pattern) null;
        var externalTransform = (String) null;
        var formatter = new MailAddressRegexFormatter(new Options(internalPattern, internalTransform, externalPattern,
                externalTransform));

        var mailAddress = "test@example.com";
        var formattedAddress = formatter.format(mailAddress);

        assertEquals(mailAddress, formattedAddress);
    }

    @Test
    public void whenInternalTransformCapturesNothingAndNoExternalPattern_ThenFormatThrowsException() {
        var internalPattern = Pattern.compile("(.*)@test.com");
        var internalTransform = "%s@test2.com";
        var externalPattern = (Pattern) null;
        var externalTransform = (String) null;
        var formatter = new MailAddressRegexFormatter(new Options(internalPattern, internalTransform, externalPattern,
                externalTransform));

        var mailAddress = "test@example.com";

        assertThrows(IllegalArgumentException.class, () -> formatter.format(mailAddress));
    }

    @Test
    public void whenInternalTransformCapturesNothingAndExternalCapturesRawAddress_ThenFormatReturnsRawAddress() {
        var internalPattern = Pattern.compile("(.*)@test.com");
        var internalTransform = "%s@test.com";
        var externalPattern = Pattern.compile("(.*)");
        var externalTransform = "%s";
        var formatter = new MailAddressRegexFormatter(new Options(internalPattern, internalTransform, externalPattern,
                externalTransform));

        var mailAddress = "test@example.com";
        var formattedAddress = formatter.format(mailAddress);

        assertEquals(mailAddress, formattedAddress);
    }

    @Test
    public void whenInternalTransformCapturesNothingAndExternalCapturesNothing_ThenFormatThrowsException() {
        var internalPattern = Pattern.compile("(.*)@test.com");
        var internalTransform = "%s@test.com";
        var externalPattern = Pattern.compile("(.*)@domain.com");
        var externalTransform = "%s@domain.com";
        var formatter = new MailAddressRegexFormatter(
                new Options(internalPattern, internalTransform, externalPattern, externalTransform));

        var mailAddress = "test@example.com";

        assertThrows(IllegalArgumentException.class, () -> formatter.format(mailAddress));
    }

    @Test
    public void whenInternalTransformCapturesGroup_ThenFormatReturnsTransformedAddress() {
        var internalPattern = Pattern.compile("(.*)@example.com");
        var internalTransform = "%s@test.com";
        var externalPattern = (Pattern) null;
        var externalTransform = (String) null;
        var formatter = new MailAddressRegexFormatter(new Options(internalPattern, internalTransform, externalPattern,
                externalTransform));

        var mailAddress = "test@example.com";
        var formattedAddress = formatter.format(mailAddress);

        assertEquals("test@test.com", formattedAddress);
    }

    @Test
    public void whenInternalTransformCapturesMultipleGroups_ThenFormatReturnsTransformedAddress() {
        var internalPattern = Pattern.compile("(.*)@(.*).com");
        var internalTransform = "%2$s@%1$s.org";
        var externalPattern = (Pattern) null;
        var externalTransform = (String) null;
        var formatter = new MailAddressRegexFormatter(new Options(internalPattern, internalTransform, externalPattern,
                externalTransform));

        var mailAddress = "test@example.com";
        var formattedAddress = formatter.format(mailAddress);

        assertEquals("example@test.org", formattedAddress);
    }

    @Test
    public void whenInternalTransformCapturesNothingAndExternalTransformCapturesGroup_ThenFormatReturnsTransformedAddress() {
        var internalPattern = Pattern.compile("(.*)@example.com");
        var internalTransform = "%s@test.com";
        var externalPattern = Pattern.compile("(.*)@external.com");
        var externalTransform = "%s@example.external.com";
        var formatter = new MailAddressRegexFormatter(new Options(internalPattern, internalTransform, externalPattern,
                externalTransform));

        var mailAddress = "test@external.com";
        var formattedAddress = formatter.format(mailAddress);

        assertEquals("test@example.external.com", formattedAddress);
    }

    @Test
    public void whenInternalTransformCapturesNothingAndExternalTransformCapturesMultipleGroups_ThenFormatReturnsTransformedAddress() {
        var internalPattern = Pattern.compile("(.*)@(.*).com");
        var internalTransform = "%2$s@%1$s.org";
        var externalPattern = Pattern.compile("(.*)@(.*).io");
        var externalTransform = "%2$s@%1$s.net";
        var formatter = new MailAddressRegexFormatter(new Options(internalPattern, internalTransform, externalPattern,
                externalTransform));

        var mailAddress = "test@example.io";
        var formattedAddress = formatter.format(mailAddress);

        assertEquals("example@test.net", formattedAddress);
    }
}
