package be.kbc.savingstreak.service;

/** Thrown when a request is well formed but breaks a banking rule. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
