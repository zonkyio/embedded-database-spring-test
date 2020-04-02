package io.zonky.test.db.provider;

import org.springframework.core.NestedRuntimeException;

public class ProviderException extends NestedRuntimeException {

    /**
     * Construct a new provider exception.
     *
     * @param message the exception message
     */
    public ProviderException(String message) {
        super(message);
    }

    /**
     * Construct a new provider exception.
     *
     * @param message the exception message
     * @param cause the cause
     */
    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
