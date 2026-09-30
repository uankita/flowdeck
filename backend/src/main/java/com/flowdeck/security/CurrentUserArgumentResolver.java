package com.flowdeck.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Backs {@link CurrentUser} — registered via {@code WebConfig}. */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthenticatedUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        // Only reachable if @CurrentUser is used on an endpoint that isn't
        // actually behind the JWT filter (a config mistake) — a genuinely
        // unauthenticated request never reaches the controller at all, since
        // authorizeHttpRequests() rejects it first.
        throw new IllegalStateException(
                "@CurrentUser was used on an endpoint with no authenticated principal in the "
                        + "SecurityContext — is it covered by authorizeHttpRequests().authenticated()?");
    }
}
