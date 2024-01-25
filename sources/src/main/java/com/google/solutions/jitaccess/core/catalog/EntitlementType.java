package com.google.solutions.jitaccess.core.catalog;

public enum EntitlementType {
    JIT             (ActivationType.SELF_APPROVAL),
    PEER            (ActivationType.PEER_APPROVAL),
    REQUESTER       (ActivationType.EXTERNAL_APPROVAL),
    REVIEWER        (ActivationType.NONE),
    NONE            (ActivationType.NONE);

    public final ActivationType activationType;

    EntitlementType (ActivationType activationType) {
        this.activationType = activationType;
    }
}
