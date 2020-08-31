package dev.squaremile.asynctcp.domain.connection;

import dev.squaremile.asynctcp.domain.api.ConnectionIdValue;
import dev.squaremile.asynctcp.domain.api.commands.CloseConnection;
import dev.squaremile.asynctcp.domain.api.commands.CommandFactory;
import dev.squaremile.asynctcp.domain.api.commands.ConnectionCommand;
import dev.squaremile.asynctcp.domain.api.commands.NoOpCommand;
import dev.squaremile.asynctcp.domain.api.commands.ReadData;
import dev.squaremile.asynctcp.domain.api.commands.SendData;

public class ConnectionCommands
{
    private final SendData sendDataCommand;
    private final ReadData readDataCommand;
    private final CloseConnection closeConnection;
    private final NoOpCommand noOpCommand;

    public ConnectionCommands(final ConnectionIdValue connectionId, final int initialSenderBufferSize)
    {
        CommandFactory commandFactory = new CommandFactory();
        this.sendDataCommand = new SendData(connectionId, initialSenderBufferSize);
        this.readDataCommand = commandFactory.create(connectionId, ReadData.class);
        this.closeConnection = commandFactory.create(connectionId, CloseConnection.class);
        this.noOpCommand = commandFactory.create(connectionId, NoOpCommand.class);
    }

    public <C extends ConnectionCommand> C command(final Class<C> commandType)
    {
        if (commandType.equals(SendData.class))
        {
            return commandType.cast(sendDataCommand.reset());
        }
        if (commandType.equals(ReadData.class))
        {
            return commandType.cast(readDataCommand);
        }
        if (commandType.equals(CloseConnection.class))
        {
            return commandType.cast(closeConnection);
        }
        if (commandType.equals(NoOpCommand.class))
        {
            return commandType.cast(noOpCommand);
        }
        throw new IllegalArgumentException(commandType.getSimpleName());
    }
}
