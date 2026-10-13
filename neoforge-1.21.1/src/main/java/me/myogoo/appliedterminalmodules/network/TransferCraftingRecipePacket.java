package me.myogoo.appliedterminalmodules.network;

import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.menu.CraftingRecipeTransfer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only identifiers cross the wire. Ingredients and inventories are resolved on the server. */
public record TransferCraftingRecipePacket(int containerId, ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<TransferCraftingRecipePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, "transfer_crafting_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransferCraftingRecipePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, TransferCraftingRecipePacket::containerId,
                    ResourceLocation.STREAM_CODEC, TransferCraftingRecipePacket::recipeId,
                    TransferCraftingRecipePacket::new);

    @Override
    public Type<TransferCraftingRecipePacket> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        TransferWorkstationRecipePacket.register(event);
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                CraftingRecipeTransfer.transfer(player, packet.containerId(), packet.recipeId());
            }
        });
    }
}
