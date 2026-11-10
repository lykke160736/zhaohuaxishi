package com.zhx.exception;

public class HttpError extends RuntimeException {
    private final int status;

    public HttpError(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static HttpError bad(String message) {
        return new HttpError(400, message);
    }

    public static HttpError unauthorized(String message) {
        return new HttpError(401, message);
    }

    public static HttpError forbidden(String message) {
        return new HttpError(403, message);
    }
}
