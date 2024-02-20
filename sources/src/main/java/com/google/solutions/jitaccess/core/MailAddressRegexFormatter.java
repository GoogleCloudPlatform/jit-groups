package com.google.solutions.jitaccess.core;

import java.util.regex.Pattern;
import java.util.stream.IntStream;

import com.google.common.base.Preconditions;

import jakarta.inject.Singleton;

@Singleton
public class MailAddressRegexFormatter implements MailAddressFormatter {
    private final Options options;

    public MailAddressRegexFormatter(Options options) {
        this.options = options;
    }

    public record Options(Pattern internalsMailAddressPattern, String internalsMailAddressTransform,
            Pattern externalsMailAddressPattern, String externalsMailAddressTransform) {
        public Options {
            Preconditions.checkNotNull(internalsMailAddressPattern);
            Preconditions.checkNotNull(internalsMailAddressTransform);
        }
    }

    public Options options() {
        return this.options;
    }

    @Override
    public String format(String mailAddress) {
        var formattedAddress = transform(mailAddress, options().internalsMailAddressPattern,
                options().internalsMailAddressTransform);
        if (formattedAddress != null) {
            return formattedAddress;
        }

        if (options.externalsMailAddressPattern == null) {
            throw new IllegalArgumentException("Email address does not conform to configured patterns.");
        }

        formattedAddress = transform(mailAddress, options().externalsMailAddressPattern,
                options().externalsMailAddressTransform);
        if (formattedAddress != null) {
            return formattedAddress;
        }

        throw new IllegalArgumentException("Email address does not conform to configured patterns.");
    }

    private String transform(String original, Pattern pattern, String transform) {
        var matcher = pattern.matcher(original);

        if (matcher.matches()) {
            var groups = IntStream.range(1, matcher.groupCount() + 1).mapToObj((i) -> matcher.group(i)).toArray();
            return String.format(transform, groups);
        }

        return null;
    }
}
