package org.v.shortly.exception;
public class NotFoundException extends RuntimeException{   // RuntimeException = "unchecked": no forced try/catch everywhere
    public NotFoundException(String message){
        super(message);   // pass the message up to the parent class
    }
}