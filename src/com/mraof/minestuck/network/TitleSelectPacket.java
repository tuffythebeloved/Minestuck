package com.mraof.minestuck.network;

import com.mraof.minestuck.client.gui.MSScreenFactories;
import com.mraof.minestuck.network.skaianet.SburbHandler;
import com.mraof.minestuck.util.Title;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;

public class TitleSelectPacket implements PlayToBothPacket
{
	private final Title title;
	
	public TitleSelectPacket()
	{
		title = null;
	}
	
	public TitleSelectPacket(Title title)
	{
		this.title = title;
	}
	
	public void encode(PacketBuffer buffer)
	{
		if(title != null)
		{
			title.write(buffer);
		}
	}
	
	public static TitleSelectPacket decode(PacketBuffer buffer)
	{
		if(buffer.readableBytes() > 0)
		{
			Title title = Title.read(buffer);
			return new TitleSelectPacket(title);
		} else return new TitleSelectPacket();
	}
	
	@Override
	public void execute()
	{
		MSScreenFactories.displayTitleSelectScreen(title);
	}
	
	@Override
	public void execute(ServerPlayerEntity player)
	{
		SburbHandler.titleSelected(player, title);
	}
}