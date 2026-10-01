package com.lifepedia.api.finance.group;

/** Como um item pessoal vai para o grupo. */
public enum ShareMode {
    /** Espelho: muda e some junto com o item pessoal. */
    SYNC,
    /** Cópia independente, editável no grupo por quem levou. */
    COPY
}
