package com.hsu.oneclickmeet.global.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.sql.SQLException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTests {

	private MockMvc mvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mvc = standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@AfterEach
	void closeValidator() {
		validator.close();
	}

	@Test
	void 업무예외의_상태와_오류코드를_반환한다() throws Exception {
		failure(get("/test/not-found"), 404, "COURSE-0404")
			.andExpect(jsonPath("$.error.message").value("존재하지 않는 코스입니다."))
			.andExpect(jsonPath("$.error.errors").doesNotHaveJsonPath());
	}

	@Test
	void 도메인의_400을_공통_요청오류로_덮어쓰지_않는다() throws Exception {
		failure(get("/test/domain-input"), 400, "COURSE-0400")
			.andExpect(jsonPath("$.error.message").value("코스 이름을 확인해 주세요."))
			.andExpect(jsonPath("$.error.errors").doesNotHaveJsonPath());
	}

	@ParameterizedTest
	@MethodSource("invalidBodies")
	void 본문과_중첩객체의_검증오류를_필드별로_반환한다(String body, String field, String message)
		throws Exception {
		failure(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body), 400, "COMMON-0400")
			.andExpect(result -> assertThat(result.getResolvedException())
				.isInstanceOf(MethodArgumentNotValidException.class))
			.andExpect(jsonPath("$.error.errors.length()").value(1))
			.andExpect(jsonPath("$.error.errors[0].field").value(field))
			.andExpect(jsonPath("$.error.errors[0].message").value(message));
	}

	static Stream<Arguments> invalidBodies() {
		return Stream.of(
			Arguments.of("{\"name\":\"\",\"address\":{\"street\":\"서울\"}}", "name", "이름은 필수입니다."),
			Arguments.of("{\"name\":\"코스\",\"address\":{\"street\":\"\"}}", "address.street", "주소는 필수입니다.")
		);
	}

	@Test
	void 모델속성의_검증오류를_필드별로_반환한다() throws Exception {
		failure(get("/test/model").param("name", ""), 400, "COMMON-0400")
			.andExpect(jsonPath("$.error.errors[0].field").value("name"))
			.andExpect(jsonPath("$.error.errors[0].message").value("이름은 필수입니다."));
	}

	@Test
	void 쿼리파라미터의_메서드검증_실패를_400으로_반환한다() throws Exception {
		failure(get("/test/count").param("quantity", "0"), 400, "COMMON-0400")
			.andExpect(result -> assertThat(result.getResolvedException())
				.isInstanceOf(HandlerMethodValidationException.class))
			.andExpect(jsonPath("$.error.errors[0].field").value("quantity"))
			.andExpect(jsonPath("$.error.errors[0].message").value("1 이상이어야 합니다."));
	}

	@Test
	void 반환값의_검증실패는_필드목록_없이_일반_500으로_반환한다() throws Exception {
		failure(get("/test/invalid-return"), 500, "COMMON-0500")
			.andExpect(result -> {
				assertThat(result.getResolvedException()).isInstanceOf(HandlerMethodValidationException.class);
				assertThat(((HandlerMethodValidationException) result.getResolvedException()).isForReturnValue()).isTrue();
			})
			.andExpect(jsonPath("$.error.message").value("요청 처리 중 오류가 발생했습니다."))
			.andExpect(jsonPath("$.error.errors").doesNotHaveJsonPath());
	}

	@ParameterizedTest
	@MethodSource("bindingFailures")
	void 잘못된_요청바인딩은_필드검증목록_없이_400을_반환한다(MockHttpServletRequestBuilder request)
		throws Exception {
		failure(request, 400, "COMMON-0400")
			.andExpect(jsonPath("$.error.message").value("입력값을 확인해 주세요."))
			.andExpect(jsonPath("$.error.errors").doesNotHaveJsonPath());
	}

	static Stream<MockHttpServletRequestBuilder> bindingFailures() {
		return Stream.of(
			post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"),
			get("/test/count").param("quantity", "abc"),
			get("/test/count"),
			get("/test/model").param("name", "코스").param("count", "abc")
		);
	}

	@Test
	void 지원하지_않는_HTTP메서드는_405와_Allow헤더를_유지한다() throws Exception {
		failure(get("/test/body"), 405, "COMMON-0405")
			.andExpect(header().string("Allow", containsString("POST")))
			.andExpect(jsonPath("$.error.errors").doesNotHaveJsonPath());
	}

	@Test
	void 지원하지_않는_본문형식은_415를_유지한다() throws Exception {
		failure(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("invalid"), 415, "COMMON-0415")
			.andExpect(jsonPath("$.error.errors").doesNotHaveJsonPath());
	}

	@Test
	void 예상하지_못한_예외는_정보를_숨기고_전체예외를_서버에_기록한다() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			failure(get("/test/unexpected"), 500, "COMMON-0500")
				.andExpect(jsonPath("$.error.message").value("요청 처리 중 오류가 발생했습니다."))
				.andExpect(jsonPath("$.error.errors").doesNotHaveJsonPath())
				.andExpect(content().string(not(containsString("SELECT"))))
				.andExpect(content().string(not(containsString("db-password"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))))
				.andExpect(content().string(not(containsString("stackTrace"))));
			assertThat(appender.list).anySatisfy(event -> {
				assertThat(event.getLevel()).isEqualTo(Level.ERROR);
				assertThat(event.getThrowableProxy()).isNotNull();
				assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
				assertThat(event.getThrowableProxy().getMessage()).contains("db-password");
				assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
				assertThat(event.getThrowableProxy().getCause()).isNotNull();
				assertThat(event.getThrowableProxy().getCause().getClassName()).isEqualTo(SQLException.class.getName());
			});
		}
		finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	@Test
	void 성공응답의_null_data와_error를_생략하지_않는다() throws Exception {
		mvc.perform(get("/test/success"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data").hasJsonPath())
			.andExpect(jsonPath("$.data").value(nullValue()))
			.andExpect(jsonPath("$.error").hasJsonPath())
			.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	void 인증과_인가예외를_일반_500으로_변환하지_않는다() {
		assertThatThrownBy(() -> mvc.perform(get("/test/authentication")))
			.hasRootCauseInstanceOf(BadCredentialsException.class);
		assertThatThrownBy(() -> mvc.perform(get("/test/access")))
			.hasRootCauseInstanceOf(AccessDeniedException.class);
	}

	@Test
	void 다른_예외로_감싼_인가예외도_일반_500으로_변환하지_않는다() {
		assertThatThrownBy(() -> mvc.perform(get("/test/wrapped-access")))
			.hasRootCauseInstanceOf(AccessDeniedException.class);
	}

	private ResultActions failure(MockHttpServletRequestBuilder request, int httpStatus, String code) throws Exception {
		return mvc.perform(request)
			.andExpect(status().is(httpStatus))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").hasJsonPath())
			.andExpect(jsonPath("$.data").value(nullValue()))
			.andExpect(jsonPath("$.error.code").value(code));
	}

	@RestController
	static class TestController {
		@GetMapping("/test/not-found")
		ApiResponse<Void> notFound() { throw new BusinessException(CourseErrorCode.NOT_FOUND); }

		@GetMapping("/test/domain-input")
		ApiResponse<Void> domainInput() { throw new BusinessException(new ErrorCode() {
			public HttpStatus getStatus() { return HttpStatus.BAD_REQUEST; }
			public String getCode() { return "COURSE-0400"; }
			public String getMessage() { return "코스 이름을 확인해 주세요."; }
		}); }

		@PostMapping("/test/body")
		ApiResponse<Void> body(@Valid @RequestBody CreateRequest request) { return ApiResponse.success(null); }

		@GetMapping("/test/model")
		ApiResponse<Void> model(@Valid @ModelAttribute NameQuery query) { return ApiResponse.success(null); }

		@GetMapping("/test/count")
		ApiResponse<Void> count(@RequestParam("quantity") @Min(value = 1, message = "1 이상이어야 합니다.") int count) {
			return ApiResponse.success(null);
		}

		@NotBlank(message = "반환값 내부 검증 실패")
		@GetMapping("/test/invalid-return")
		String invalidReturn() { return ""; }

		@GetMapping("/test/unexpected")
		ApiResponse<Void> unexpected() {
			throw new IllegalStateException("db-password=secret", new SQLException("SELECT password FROM members"));
		}

		@GetMapping("/test/success")
		ApiResponse<Void> success() { return ApiResponse.success(null); }

		@GetMapping("/test/authentication")
		ApiResponse<Void> authentication() { throw new BadCredentialsException("인증 실패"); }

		@GetMapping("/test/access")
		ApiResponse<Void> access() { throw new AccessDeniedException("접근 거부"); }

		@GetMapping("/test/wrapped-access")
		ApiResponse<Void> wrappedAccess() {
			throw new IllegalStateException("보안 계층 오류", new AccessDeniedException("접근 거부"));
		}
	}

	record CreateRequest(@NotBlank(message = "이름은 필수입니다.") String name,
		@NotNull @Valid AddressRequest address) { }

	record AddressRequest(@NotBlank(message = "주소는 필수입니다.") String street) { }

	record NameQuery(@NotBlank(message = "이름은 필수입니다.") String name, Integer count) { }
}
