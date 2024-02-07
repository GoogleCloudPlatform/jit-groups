package com.google.solutions.jitaccess.core.catalog;

public class ExternalApproval extends ActivationType {

    private final String name = "EXTERNAL_APPROVAL";
    private final String topic;

    public ExternalApproval(String topic) {
        this.topic = topic;
    }

    @Override
    public String name() {
        return this.name + "(" + this.topic + ")";
    }

    @Override
    public boolean contains(ActivationType other) {
        if (!(other instanceof ExternalApproval)) {
            return false;
        }

        if (this.topic.equals("")) {
            return true;
        }

        return this.name().equals(other.name());
    }

}
