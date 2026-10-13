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

/** The server resolves the recipe, workstation kind and inputs. No item stacks cross the wire. */
public record TransferWorkstationRecipePacket(int containerId, ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<TransferWorkstationRecipePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, "transfer_workstation_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransferWorkstationRecipePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, TransferWorkstationRecipePacket::containerId,
                    ResourceLocation.STREAM_CODEC, TransferWorkstationRecipePacket::recipeId,
                    TransferWorkstationRecipePacket::new);

    @Override
    public Type<TransferWorkstationRecipePacket> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                CraftingRecipeTransfer.transferWorkstation(player, packet.containerId(), packet.recipeId());
            }
        });
    }
}
