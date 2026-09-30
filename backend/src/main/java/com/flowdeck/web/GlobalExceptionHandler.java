package com.flowdeck.web;

import com.flowdeck.security.InvalidRefreshTokenException;
import com.flowdeck.service.BoardListNotFoundException;
import com.flowdeck.service.BoardListVersionConflictException;
import com.flowdeck.service.BoardNotFoundException;
import com.flowdeck.service.CardNotFoundException;
import com.flowdeck.service.CardVersionConflictException;
import com.flowdeck.service.DuplicateBoardKeyException;
import com.flowdeck.service.DuplicateLabelNameException;
import com.flowdeck.service.DuplicateWorkspaceSlugException;
import com.flowdeck.service.EmailAlreadyRegisteredException;
import com.flowdeck.service.InvalidCardMoveException;
import com.flowdeck.service.InvalidCredentialsException;
import com.flowdeck.service.InvalidListMoveException;
import com.flowdeck.service.InvalidPasswordException;
import com.flowdeck.service.LabelNotFoundException;
import com.flowdeck.service.WorkspaceNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain failures onto RFC 7807 problem responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BoardNotFoundException.class)
    public ProblemDetail handleBoardNotFound(BoardNotFoundException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Board not found");
        return problem;
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ProblemDetail handleWorkspaceNotFound(WorkspaceNotFoundException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Workspace not found");
        return problem;
    }

    @ExceptionHandler(CardNotFoundException.class)
    public ProblemDetail handleCardNotFound(CardNotFoundException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Card not found");
        return problem;
    }

    @ExceptionHandler(BoardListNotFoundException.class)
    public ProblemDetail handleBoardListNotFound(BoardListNotFoundException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("List not found");
        return problem;
    }

    @ExceptionHandler(InvalidCardMoveException.class)
    public ProblemDetail handleInvalidCardMove(InvalidCardMoveException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid card move");
        return problem;
    }

    @ExceptionHandler(InvalidListMoveException.class)
    public ProblemDetail handleInvalidListMove(InvalidListMoveException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid list move");
        return problem;
    }

    @ExceptionHandler(LabelNotFoundException.class)
    public ProblemDetail handleLabelNotFound(LabelNotFoundException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Label not found");
        return problem;
    }

    /**
     * The task's "409 with current server state in the body" requirement,
     * concretely: {@code currentState} is an RFC 7807 extension member (a
     * plain additional field on the problem+json body) carrying the card as
     * it actually is right now, so a client can resolve the conflict without
     * a separate GET.
     */
    @ExceptionHandler(CardVersionConflictException.class)
    public ProblemDetail handleCardVersionConflict(CardVersionConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Card was modified concurrently");
        problem.setProperty("currentState", ex.getCurrentState());
        return problem;
    }

    /** Same shape as {@link #handleCardVersionConflict}, for {@code BoardList} updates. */
    @ExceptionHandler(BoardListVersionConflictException.class)
    public ProblemDetail handleBoardListVersionConflict(BoardListVersionConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("List was modified concurrently");
        problem.setProperty("currentState", ex.getCurrentState());
        return problem;
    }

    /**
     * Backstop for an optimistic-lock conflict that reaches here unwrapped —
     * every entity that carries {@code @Version} (Card, BoardList) is
     * expected to be caught and re-thrown as one of the two handlers above
     * instead, with the current state attached; this exists only so a gap in
     * that coverage fails as a clean 409 rather than an unmapped 500.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleGenericOptimisticLockConflict(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT, "This resource was modified concurrently — reload and retry");
        problem.setTitle("Modified concurrently");
        return problem;
    }

    @ExceptionHandler(DuplicateBoardKeyException.class)
    public ProblemDetail handleDuplicateBoardKey(DuplicateBoardKeyException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Board key already in use");
        return problem;
    }

    @ExceptionHandler(DuplicateLabelNameException.class)
    public ProblemDetail handleDuplicateLabelName(DuplicateLabelNameException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Label name already in use");
        return problem;
    }

    @ExceptionHandler(DuplicateWorkspaceSlugException.class)
    public ProblemDetail handleDuplicateWorkspaceSlug(DuplicateWorkspaceSlugException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Workspace slug already in use");
        return problem;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Email already registered");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        // Fixed, generic detail — deliberately not ex.getMessage(); see the
        // exception's Javadoc on why login must not reveal which part failed.
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        problem.setTitle("Invalid credentials");
        return problem;
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        problem.setTitle("Invalid refresh token");
        return problem;
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ProblemDetail handleInvalidPassword(InvalidPasswordException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid password");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + " " + error.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Request validation failed");
        return problem;
    }
}
