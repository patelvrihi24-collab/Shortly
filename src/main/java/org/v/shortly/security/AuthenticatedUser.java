package org.v.shortly.security;

public record AuthenticatedUser(Long id, String email) {}
// What we put in Spring's "current user" slot once a token is verified.
// What we put in Spring's "current user" slot once a token is verified.