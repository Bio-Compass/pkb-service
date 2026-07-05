package com.biocompass.pkb.command.handler;

import com.biocompass.pkb.command.dto.PkbCommand;

public interface PkbCommandHandler<C extends PkbCommand<R>, R> {

    Class<C> commandType();

    R handle(C command);
}
