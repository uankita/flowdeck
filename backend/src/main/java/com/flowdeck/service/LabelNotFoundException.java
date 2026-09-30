package com.flowdeck.service;

import java.util.UUID;

public class LabelNotFoundException extends RuntimeException {

    public LabelNotFoundException(UUID labelId) {
        super("No label with id '" + labelId + "'");
    }
}
