package com.beergame.backend.exception;

public class EmailIdAlreadyExistsException extends RuntimeException {

    public EmailIdAlreadyExistsException(String message){

        super(message);
    }

}
