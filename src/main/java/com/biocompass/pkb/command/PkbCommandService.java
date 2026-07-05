package com.biocompass.pkb.command;

import com.biocompass.pkb.command.dto.PkbCommand;
import com.biocompass.pkb.command.handler.PkbCommandHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PkbCommandService {

    private static final Method HANDLE_METHOD = handleMethod();

    private final Map<Class<?>, PkbCommandHandler<?, ?>> handlersByCommandType;
    private final Validator validator;

    public PkbCommandService(List<PkbCommandHandler<?, ?>> handlers, Validator validator) {
        var mappedHandlers = new LinkedHashMap<Class<?>, PkbCommandHandler<?, ?>>();
        for (var handler : handlers) {
            var previous = mappedHandlers.putIfAbsent(handler.commandType(), handler);
            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple PKB command handlers registered for %s".formatted(handler.commandType().getName())
                );
            }
        }
        handlersByCommandType = Map.copyOf(mappedHandlers);
        this.validator = validator;
    }

    public <R> R handle(@Valid @NotNull(message = "command is required") PkbCommand<R> command) {
        validateHandleArgument(command);
        var handler = handlersByCommandType.get(command.getClass());
        if (handler == null) {
            throw new IllegalArgumentException(
                    "No PKB command handler registered for %s".formatted(command.getClass().getName())
            );
        }
        return dispatch(handler, command);
    }

    private void validateHandleArgument(PkbCommand<?> command) {
        var violations = validator.forExecutables()
                .validateParameters(this, HANDLE_METHOD, new Object[]{command});
        if (!violations.isEmpty()) {
            var messages = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .toList();
            throw new PkbCommandValidationException(messages);
        }
    }

    private static Method handleMethod() {
        try {
            return PkbCommandService.class.getMethod("handle", PkbCommand.class);
        } catch (NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <R> R dispatch(PkbCommandHandler<?, ?> handler, PkbCommand<R> command) {
        return (R) ((PkbCommandHandler) handler).handle(command);
    }
}
