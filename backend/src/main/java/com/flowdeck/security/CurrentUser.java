package com.flowdeck.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resolves a controller method parameter of type {@link AuthenticatedUser} to
 * the caller identified by the request's verified access token.
 *
 * <pre>{@code
 * @GetMapping
 * public UserSummaryResponse me(@CurrentUser AuthenticatedUser currentUser) { ... }
 * }</pre>
 *
 * <p>Only usable behind the security filter chain — see
 * {@link CurrentUserArgumentResolver} for what happens otherwise.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {}
