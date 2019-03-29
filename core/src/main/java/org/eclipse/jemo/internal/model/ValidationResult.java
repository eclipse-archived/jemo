package org.eclipse.jemo.internal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;


/**
 * Models the result of permissions validation
 */
public class ValidationResult {
    public static final ValidationResult SUCCESS = new ValidationResult(Collections.emptyList());

    @JsonProperty
    private final List<String> notAllowedActions;

    public ValidationResult(List<String> notPermittedActions) {
        this.notAllowedActions = notPermittedActions;
    }

    public List<String> notAllowedActions() {
        return notAllowedActions;
    }

    public boolean isSuccess() {
        return notAllowedActions.isEmpty();
    }

    @Override
    public String toString() {
        return isSuccess() ?
                "Success: All needed actions are permitted." :
                "Failure: The following actions are not permitted: " + String.join(", ", notAllowedActions);
    }
}
