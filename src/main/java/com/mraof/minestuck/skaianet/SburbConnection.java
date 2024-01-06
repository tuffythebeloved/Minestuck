package com.mraof.minestuck.skaianet;

import com.mraof.minestuck.computer.ComputerReference;
import com.mraof.minestuck.computer.ISburbComputer;
import com.mraof.minestuck.player.IdentifierHandler;
import com.mraof.minestuck.player.PlayerIdentifier;
import com.mraof.minestuck.player.PlayerSavedData;
import com.mraof.minestuck.player.Title;
import com.mraof.minestuck.skaianet.client.ReducedConnection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public final class SburbConnection
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	final SkaianetHandler skaianet;
	
	@Nonnull
	private final PlayerIdentifier clientIdentifier;
	@Nonnull
	private PlayerIdentifier serverIdentifier;
	final SburbPlayerData playerData = new SburbPlayerData(this);
	@Nullable
	private ActiveConnection activeConnection;
	private boolean isMain;
	
	SburbConnection(PlayerIdentifier client, SkaianetHandler skaianet)
	{
		this(client, IdentifierHandler.NULL_IDENTIFIER, skaianet);
	}
	
	SburbConnection(PlayerIdentifier client, PlayerIdentifier server, SkaianetHandler skaianet)
	{
		clientIdentifier = Objects.requireNonNull(client);
		serverIdentifier = Objects.requireNonNull(server);
		this.skaianet = skaianet;
	}
	
	SburbConnection(CompoundTag nbt, SkaianetHandler skaianet)
	{
		this.skaianet = skaianet;
		isMain = nbt.getBoolean("IsMain");
		boolean active = true;
		
		if(isMain)
			active = nbt.getBoolean("IsActive");
		
		clientIdentifier = IdentifierHandler.load(nbt, "client");
		serverIdentifier = IdentifierHandler.load(nbt, "server");
		
		if(active)
		{
			try
			{
				ComputerReference clientComputer = ComputerReference.read(nbt.getCompound("client_computer"));
				ComputerReference serverComputer = ComputerReference.read(nbt.getCompound("server_computer"));
				activeConnection = new ActiveConnection(this, clientComputer, serverComputer);
			} catch(Exception e)
			{
				LOGGER.error("Unable to read computer position for sburb connection between {} and {}, setting connection to be inactive. Cause: ", clientIdentifier.getUsername(), serverIdentifier.getUsername(), e);
			}
		}
		
		playerData.read(nbt, this.isMain);
	}
	
	CompoundTag write()
	{
		CompoundTag nbt = new CompoundTag();
		nbt.putBoolean("IsMain", isMain);
		if(isMain)
		{
			nbt.putBoolean("IsActive", activeConnection != null);
		}
		
		getClientIdentifier().saveToNBT(nbt, "client");
		getServerIdentifier().saveToNBT(nbt, "server");
		
		if(activeConnection != null)
		{
			nbt.put("client_computer", activeConnection.clientComputer().write(new CompoundTag()));
			nbt.put("server_computer", activeConnection.serverComputer().write(new CompoundTag()));
		}
		
		playerData.write(nbt, this.isMain);
		
		return nbt;
	}
	
	public Session getSession()
	{
		return skaianet.sessionHandler.getPlayerSession(this.getClientIdentifier());
	}
	
	void copyComputerReferences(SburbConnection connection)
	{
		if(!connection.isActive() || !connection.getClientIdentifier().equals(clientIdentifier)
				|| !connection.getServerIdentifier().equals(serverIdentifier))
			throw new IllegalArgumentException();
		activeConnection = connection.activeConnection;
	}
	
	void setActive(ComputerReference client, ComputerReference server)
	{
		if(isActive())
			throw new IllegalStateException("Should not activate sburb connection when already active");
		
		activeConnection = new ActiveConnection(this, client, server);
	}
	
	void close()
	{
		activeConnection = null;
		skaianet.infoTracker.markDirty(this);
	}
	
	@Nonnull
	public PlayerIdentifier getClientIdentifier()
	{
		return clientIdentifier;
	}
	
	@Nonnull
	public PlayerIdentifier getServerIdentifier()
	{
		return serverIdentifier;
	}
	
	public boolean hasServerPlayer()
	{
		return getServerIdentifier() != IdentifierHandler.NULL_IDENTIFIER;
	}
	
	void removeServerPlayer()
	{
		Session session = this.getSession();
		skaianet.infoTracker.markDirty(this);
		serverIdentifier = IdentifierHandler.NULL_IDENTIFIER;
		skaianet.sessionHandler.onConnectionChainBroken(session);
	}
	
	void setNewServerPlayer(PlayerIdentifier server) throws MergeResult.SessionMergeException
	{
		if(hasServerPlayer())
			throw new IllegalStateException("Connection already has server player");
		if(skaianet.getPrimaryConnection(server, false).isPresent())
			throw MergeResult.GENERIC_FAIL.exception();
		skaianet.sessionHandler.prepareSessionFor(clientIdentifier, server);    //Make sure that it is fine to add the server here session-wise
		
		serverIdentifier = Objects.requireNonNull(server);
		skaianet.infoTracker.markDirty(this);
	}
	
	boolean hasPlayer(PlayerIdentifier player)
	{
		return clientIdentifier.equals(player) || serverIdentifier.equals(player);
	}
	
	@Nullable
	public ActiveConnection getActiveConnection()
	{
		return activeConnection;
	}
	
	public SburbPlayerData data()
	{
		return this.playerData;
	}
	
	void updateComputer(ISburbComputer oldComputer, ComputerReference newComputer)
	{
		Objects.requireNonNull(newComputer);
		if(activeConnection != null)
		{
			if(activeConnection.clientComputer().matches(oldComputer))
				activeConnection = new ActiveConnection(this, newComputer, activeConnection.serverComputer());
			if(activeConnection.serverComputer().matches(oldComputer))
				activeConnection = new ActiveConnection(this, activeConnection.clientComputer(), newComputer);
		}
	}
	
	public boolean isMain()
	{
		return isMain;
	}
	
	public boolean isActive()
	{
		return activeConnection != null;
	}
	
	void setIsMain()
	{
		if(!isMain)
		{
			isMain = true;
			skaianet.infoTracker.markDirty(this);
		}
	}
	
	public boolean isLockedToSession()
	{
		return false;
	}
	
	public Title getClientTitle()
	{
		if(data().hasEntered())
		{
			Title title = PlayerSavedData.getData(getClientIdentifier(), skaianet.mcServer).getTitle();
			if(title == null)
				LOGGER.warn("Found player {} that has entered, but did not have a title!", getClientIdentifier().getUsername());
			return title;
		}
		return null;
	}
	
	void copyFrom(SburbConnection other)
	{
		this.playerData.copyFrom(other.playerData);
	}
	
	/**
	 * Writes the connection info needed client-side to a network buffer. Must match with {@link ReducedConnection#read}.
	 */
	public void toBuffer(FriendlyByteBuf buffer)
	{
		buffer.writeBoolean(isMain);
		if(isMain)
		{
			buffer.writeBoolean(isActive());
			buffer.writeBoolean(playerData.hasEntered());
		}
		buffer.writeInt(getClientIdentifier().getId());
		buffer.writeUtf(getClientIdentifier().getUsername(), 16);
		buffer.writeInt(getServerIdentifier().getId());
		buffer.writeUtf(getServerIdentifier().getUsername(), 16);
	}
}
