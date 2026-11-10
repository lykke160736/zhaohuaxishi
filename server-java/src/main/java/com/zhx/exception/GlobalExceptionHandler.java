package com.zhx.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, Object>> error(int status, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("msg", msg);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(HttpError.class)
    public ResponseEntity<Map<String, Object>> handleHttpError(HttpError e) {
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception e) {
        return error(404, "接口不存在");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadJson(HttpMessageNotReadableException e) {
        return error(400, "请求体不是合法 JSON");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return error(413, "请求体过大");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception e) {
        log.error("api error", e);
        return error(500, "服务器内部错误");
    }
}
