package com.mraof.minestuck.world;

import com.mraof.minestuck.Minestuck;
import com.mraof.minestuck.network.skaianet.SkaianetHandler;
import com.mraof.minestuck.util.Debug;
import com.mraof.minestuck.world.lands.LandInfoContainer;
import com.mraof.minestuck.world.lands.LandTypePair;
import com.mraof.minestuck.world.lands.LandTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.RegisterDimensionsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Minestuck.MOD_ID, bus=Mod.EventBusSubscriber.Bus.FORGE)
public class MSDimensions
{
	private static final ResourceLocation SKAIA_ID = new ResourceLocation(Minestuck.MOD_ID, "skaia");
	
	public static DimensionType skaiaDimension;
	
	/**
	 * On server init, this function is called to register dimensions.
	 * The dimensions registered will then be sent and registered by forge client-side.
	 */
	@SubscribeEvent
	public static void registerDimensionTypes(final RegisterDimensionsEvent event)
	{
		//register dimensions
		skaiaDimension = DimensionManager.registerOrGetDimension(SKAIA_ID, MSDimensionTypes.SKAIA, null, true);
	}
	
	public static LandTypePair getAspects(MinecraftServer server, DimensionType dimension)
	{
		LandInfoContainer info = getLandInfo(server, dimension);
		if(info != null)
			return info.getLandAspects();
		else if(isLandDimension(dimension))
		{
			Debug.warnf("Tried to get land aspects for %s, but did not find a container reference! Using defaults instead.");
			return new LandTypePair(LandTypes.TERRAIN_NULL, LandTypes.TITLE_NULL);
		} else return null;
	}
	
	public static LandInfoContainer getLandInfo(MinecraftServer server, DimensionType dimension)
	{
		return SkaianetHandler.get(server).landInfoForDimension(dimension);
	}
	
	public static boolean isLandDimension(DimensionType dimension)
	{
		return dimension != null && dimension.getModType() == MSDimensionTypes.LANDS;
	}
	
	public static boolean isSkaia(DimensionType dimension)
	{
		return dimension != null && dimension.getModType() == MSDimensionTypes.SKAIA;
	}
}