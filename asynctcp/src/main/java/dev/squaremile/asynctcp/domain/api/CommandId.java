package dev.squaremile.asynctcp.domain.api;

public interface CommandId
{
    long NO_COMMAND_ID = -1;

    long commandId();
}