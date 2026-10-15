package com.lucchetta.core.port;

/** Produces new league invite codes, hard to guess. */
public interface InviteCodeGenerator {

    String next();
}
