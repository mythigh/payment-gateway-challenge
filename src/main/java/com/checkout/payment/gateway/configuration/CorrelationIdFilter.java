package com.checkout.payment.gateway.configuration;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  public static final String CORRELATION_ID_MDC_KEY = "correlationId";
  private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String correlationId = getCorrelationId(request.getHeader(CORRELATION_ID_HEADER));
    response.setHeader(CORRELATION_ID_HEADER, correlationId);
    MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(CORRELATION_ID_MDC_KEY);
    }
  }

  private String getCorrelationId(String suppliedCorrelationId) {
    if (suppliedCorrelationId != null
        && SAFE_CORRELATION_ID.matcher(suppliedCorrelationId).matches()) {
      return suppliedCorrelationId;
    }

    return UUID.randomUUID().toString();
  }
}