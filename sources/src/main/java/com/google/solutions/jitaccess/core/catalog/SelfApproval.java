package com.google.solutions.jitaccess.core.catalog;

public class SelfApproval extends ActivationType {

    private final String name = "SELF_APPROVAL";

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public boolean contains(ActivationType other) {
        return this.name().equals(other.name());
    }
}
