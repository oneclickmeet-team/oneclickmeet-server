package com.hsu.oneclickmeet.global.exception;

import com.hsu.oneclickmeet.global.exception.ErrorResponse.FieldErrorDetail;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus())
				.contentType(MediaType.APPLICATION_JSON)
				.body(ApiResponse.failure(ErrorResponse.from(errorCode)));
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// ModelAttribute type conversion failures are binding errors, not constraint violations.
		Object body = exception.getBindingResult().getFieldErrors().stream().anyMatch(FieldError::isBindingFailure)
				? null : validationFailure(exception.getBindingResult().getAllErrors().stream()
				.map(error -> fieldError(error instanceof FieldError field ? field.getField() : "request", error))
				.toList());
		return handleExceptionInternal(exception, body, headers, status, request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (exception.isForReturnValue()) {
			return handleExceptionInternal(exception, null, headers, status, request);
		}
		List<FieldErrorDetail> errors = new ArrayList<>();
		for (var result : exception.getParameterValidationResults()) {
			List<? extends MessageSourceResolvable> violations = result instanceof ParameterErrors parameterErrors
					? parameterErrors.getAllErrors() : result.getResolvableErrors();
			for (var error : violations) {
				if (error instanceof FieldError field && field.isBindingFailure()) {
					return handleExceptionInternal(exception, null, headers, status, request);
				}
				String field = error instanceof FieldError fieldError ? fieldError.getField()
						: parameterName(result.getMethodParameter());
				errors.add(fieldError(field, error));
			}
		}
		exception.getCrossParameterValidationResults().forEach(error -> errors.add(fieldError("request", error)));
		return handleExceptionInternal(exception, validationFailure(errors), headers, status, request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(
			Exception exception, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (status.is5xxServerError()) {
			log.error("Spring MVC 요청 처리 중 오류가 발생했습니다.", exception);
		}
		return super.handleExceptionInternal(exception, body, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> createResponseEntity(
			@Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Object response = body instanceof ApiResponse<?> ? body : commonFailure(status);
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.putAll(headers);
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);
		return new ResponseEntity<>(response, responseHeaders, status);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) throws Exception {
		// Keep authentication and authorization failures on Spring Security's existing handling path.
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof AuthenticationException || cause instanceof AccessDeniedException) {
				throw exception;
			}
		}
		log.error("예상하지 못한 요청 처리 오류가 발생했습니다.", exception);
		ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(errorCode.getStatus())
				.contentType(MediaType.APPLICATION_JSON)
				.body(ApiResponse.failure(ErrorResponse.from(errorCode)));
	}

	private ApiResponse<Void> validationFailure(List<FieldErrorDetail> errors) {
		ErrorCode errorCode = CommonErrorCode.BAD_REQUEST;
		return ApiResponse.failure(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors));
	}

	private ApiResponse<Void> commonFailure(HttpStatusCode status) {
		String message = status.is5xxServerError() ? CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
				: status.value() == 400 ? CommonErrorCode.BAD_REQUEST.getMessage() : "요청을 처리할 수 없습니다.";
		return ApiResponse.failure(new ErrorResponse("COMMON-%04d".formatted(status.value()), message));
	}

	private FieldErrorDetail fieldError(String field, MessageSourceResolvable error) {
		String message = error.getDefaultMessage();
		return new FieldErrorDetail(field, message != null ? message : CommonErrorCode.BAD_REQUEST.getMessage());
	}

	private String parameterName(MethodParameter parameter) {
		for (var annotation : parameter.getParameterAnnotations()) {
			String name = switch (annotation) {
				case RequestParam value -> value.name().isBlank() ? value.value() : value.name();
				case PathVariable value -> value.name().isBlank() ? value.value() : value.name();
				case RequestHeader value -> value.name().isBlank() ? value.value() : value.name();
				case CookieValue value -> value.name().isBlank() ? value.value() : value.name();
				default -> "";
			};
			if (!name.isBlank()) {
				return name;
			}
		}
		String name = parameter.getParameterName();
		return name != null ? name : "arg" + parameter.getParameterIndex();
	}
}
