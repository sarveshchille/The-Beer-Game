package com.beergame.backend.exception;

public class UserNameAlreadyExistsException extends RuntimeException {

    public UserNameAlreadyExistsException(String message){

        super(message);
    }

}
