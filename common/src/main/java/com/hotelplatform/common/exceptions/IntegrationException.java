package com.hotelplatform.common.exceptions;

// ошибка общения с другим сервисом, наружу идёт 502
// retryable=true - транспортная проблема, можно повторить; false - бизнес-отказ вроде 409
public class IntegrationException extends ApiException {

    private final boolean retryable;

    public IntegrationException(String message) {
        this(message, null, true);
    }

    public IntegrationException(String message, Throwable cause, boolean retryable) {
        super(502, message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
