package dev.aziz.highloadasyncservice.exception;

import dev.aziz.highloadasyncservice.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaskNotFound(TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("TASK_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidId(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_ARGUMENT", "ID задачи должен быть положительным целым числом"
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class
    })
    public ResponseEntity<ErrorResponse> handleDatabaseUnavailable(Exception ex) {
        log.error("База данных недоступна при обработке REST-запроса", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("SERVICE_UNAVAILABLE", "Сервис временно недоступен"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Сохраняем статусы стандартных ошибок Spring MVC, например 405 и 415
        if (ex instanceof org.springframework.web.ErrorResponse httpError) {
            HttpStatusCode status = httpError.getStatusCode();
            String message = status.is4xxClientError()
                    ? "Некорректный HTTP-запрос" : "Внутренняя ошибка сервиса";
            if (status.is5xxServerError()) {
                log.error("Ошибка HTTP-обработки", ex);
            }
            return ResponseEntity.status(status)
                    .headers(httpError.getHeaders())
                    .body(new ErrorResponse("HTTP_" + status.value(), message));
        }

        log.error("Неожиданная ошибка REST-запроса", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Внутренняя ошибка сервиса"));
    }

}