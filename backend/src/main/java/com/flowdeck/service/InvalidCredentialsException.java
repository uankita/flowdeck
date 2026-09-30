package com.flowdeck.service;

/**
 * Deliberately carries no detail about which part was wrong (unknown email
 * vs. wrong password) — see {@code AuthService#login} for why the check
 * itself is constant-time regardless.
 */
public class InvalidCredentialsException extends RuntimeException {}
