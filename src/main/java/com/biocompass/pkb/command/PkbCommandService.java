package com.biocompass.pkb.command;

import com.biocompass.pkb.command.dto.PkbCommand;
import com.biocompass.pkb.command.handler.PkbCommandHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Validated
public class PkbCommandService {

    private final Map<Class<?>, PkbCommandHandler<?, ?>> handlersByCommandType;

    public PkbCommandService(List<PkbCommandHandler<?, ?>> handlers) {
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
    }

    public <R> R handle(@Valid @NotNull(message = "command is required") PkbCommand<R> command) {
        var handler = handlersByCommandType.get(command.getClass());
        if (handler == null) {
            throw new IllegalArgumentException(
                    "No PKB command handler registered for %s".formatted(command.getClass().getName())
            );
        }
        return dispatch(handler, command);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <R> R dispatch(PkbCommandHandler<?, ?> handler, PkbCommand<R> command) {
        return (R) ((PkbCommandHandler) handler).handle(command);
    }
}
