package com.raketmakhim.ledger.accounting.adapter.web;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Fills a controller's Caller parameter from the X-Customer-Id header.
 * shortcut: anyone can send any id, so this proves nothing. It is the one place to swap for a verified token
 * once login exists (roadmap item 6).
 */
class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    static final String HEADER = "X-Customer-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Caller.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            throw new UnauthenticatedException("Send your customer id in the " + HEADER + " header.");
        }
        try {
            return new Caller(UUID.fromString(header.trim()));
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException("The " + HEADER + " header must be a UUID.");
        }
    }
}
