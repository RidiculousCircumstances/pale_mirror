package io.farfrontier.palemirror.domain;

/** Stable command failure category for runtime/UI diagnostics. */
public final class DomainCommandException extends IllegalArgumentException {
    public enum Code {
        INVALID_INPUT,
        UNKNOWN_REFERENCE,
        INVARIANT_VIOLATION
    }

    private final Code code;
    private final String commandType;

    public DomainCommandException(Code code, DomainCommand command, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.commandType = command.getClass().getSimpleName();
    }

    public Code code() { return code; }
    public String commandType() { return commandType; }
}
