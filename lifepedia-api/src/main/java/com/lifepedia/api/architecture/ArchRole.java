package com.lifepedia.api.architecture;

/** Papel de quem acessa um projeto, do menor para o maior; {@code OWNER} só o dono (nunca gravado). */
public enum ArchRole {
    VIEW, COMMENT, EDIT, OWNER;

    public boolean atLeast(ArchRole other) {
        return compareTo(other) >= 0;
    }
}
