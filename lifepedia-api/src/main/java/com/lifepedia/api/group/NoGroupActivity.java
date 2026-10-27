package com.lifepedia.api.group;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Escrita em rota de grupo que não conta como atividade (ex.: fixar, buscar dados de um link). */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoGroupActivity {
}
