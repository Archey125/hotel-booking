package com.hotelplatform.common.exceptions;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(404, message);
    }

    public NotFoundException(String resource, Object id) {
        super(404, "%s с id=%s не найден".formatted(resource, id));
    }
}
