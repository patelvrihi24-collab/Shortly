package org.v.shortly.exception;
public class ExpiredLinkException extends RuntimeException{
    public ExpiredLinkException(String message){
        super(message);
    }
}
//Why two separate classes: “never existed” (404) and “existed but expired” (410 Gone) are different situations,
// and the right HTTP status differs.
// Separate types let the handler map each to its own status.