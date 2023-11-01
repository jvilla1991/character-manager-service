package com.moo.charactermanagerservice.exceptions;

public class PCNotFoundException extends RuntimeException {

    public PCNotFoundException(String message) {
        super(message);
    }
}
