package com.kafein.todo.common.security;

/**
 * Token imzası, süresi veya içeriği geçersiz. Mesaj istemciye gösterilmez, yalnızca log içindir.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
