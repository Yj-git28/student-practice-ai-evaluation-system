package org.example.backend.common;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    // 关键：@PreAuthorize 抛的 AccessDeniedException 必须原样往上传，
    // 否则会被 @RestControllerAdvice 吞成 200，前端以为成功
    @ExceptionHandler(AccessDeniedException.class)
    public void handleDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }
}