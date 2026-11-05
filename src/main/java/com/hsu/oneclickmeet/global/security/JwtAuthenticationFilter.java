package com.hsu.oneclickmeet.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer 접근 토큰을 확인해 회원 번호(Long)를 인증 정보의 principal로 넣는다.
 * 토큰이 없거나 올바르지 않으면 인증 정보를 넣지 않으며, 인증이 필요한 요청은 뒤쪽에서 401로 거절된다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final JwtTokenProvider tokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
		this.tokenProvider = tokenProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			tokenProvider.parseAccessToken(header.substring(BEARER.length()).trim()).ifPresent(memberId ->
					SecurityContextHolder.getContext().setAuthentication(
							UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of())));
		}
		chain.doFilter(request, response);
	}
}
