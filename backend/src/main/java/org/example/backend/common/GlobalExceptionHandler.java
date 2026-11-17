package org.example.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    /**
     * 兜底：任何未处理异常都返回 500 + JSON。
     * 不再依赖 /error 的默认错误页做内容协商，保证 curl / 前端拿到的都是 JSON。
     * Spring 自带的 404/405/415 等（实现 ErrorResponse 的异常）保持原状态码，
     * 不要被统一吞成 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleAll(Exception e) {
        if (e instanceof ErrorResponse er) {
            HttpStatusCode status = er.getStatusCode();
            String detail = er.getBody().getDetail();
            log.warn("请求异常 {}: {}", status.value(), detail);
            return json(status, detail == null ? "请求错误" : detail);
        }
        log.error("未处理异常", e);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return json(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + msg);
    }

    private ResponseEntity<R<Void>> json(HttpStatusCode status, String msg) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(R.fail(status.value(), msg));
    }
}
