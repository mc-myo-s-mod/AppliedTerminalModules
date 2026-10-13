package me.myogoo.appliedterminalmodules.network;

import java.util.Optional;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.appliedterminalmodules.AppliedTerminalModules;
import me.myogoo.appliedterminalmodules.menu.WorkstationModuleProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class WorkstationPackets {
    private WorkstationPackets() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SelectRecipe.TYPE, SelectRecipe.CODEC, (packet, context) -> {
            var menu = validMenu(context.player(), packet.containerId());
            if (menu != null) WorkstationModuleProvider.selectRecipe(menu, packet.recipeId());
        });
        registrar.playToServer(Rename.TYPE, Rename.CODEC, (packet, context) -> {
            var menu = validMenu(context.player(), packet.containerId());
            if (menu != null) WorkstationModuleProvider.rename(menu, packet.name());
        });
        registrar.playToClient(State.TYPE, State.CODEC, (packet, context) -> {
            if (context.player().containerMenu instanceof MEStorageMenu menu
                    && menu.containerId == packet.containerId()) {
                WorkstationModuleProvider.applyClientState(menu, packet.selectedRecipe().orElse(null), packet.anvilCost());
            }
        });
    }

    private static MEStorageMenu validMenu(Player player, int containerId) {
        return player instanceof ServerPlayer serverPlayer && !serverPlayer.hasDisconnected()
                && player.isAlive() && !player.isSpectator()
                && player.containerMenu instanceof MEStorageMenu menu
                && menu.containerId == containerId && menu.isValidMenu() && menu.stillValid(player) ? menu : null;
    }

    public static void sync(MEStorageMenu menu) {
        if (menu.getPlayer() instanceof ServerPlayer player && player.containerMenu == menu) {
            PacketDistributor.sendToPlayer(player, new State(menu.containerId,
                    Optional.ofNullable(WorkstationModuleProvider.selectedRecipe(menu)),
                    WorkstationModuleProvider.anvilCost(menu)));
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AppliedTerminalModules.MOD_ID, name));
    }

    public record SelectRecipe(int containerId, ResourceLocation recipeId) implements CustomPacketPayload {
        public static final Type<SelectRecipe> TYPE = id("select_stonecutting_recipe");
        public static final StreamCodec<RegistryFriendlyByteBuf, SelectRecipe> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SelectRecipe::containerId, ResourceLocation.STREAM_CODEC,
                SelectRecipe::recipeId, SelectRecipe::new);
        @Override public Type<SelectRecipe> type() { return TYPE; }
    }

    public record Rename(int containerId, String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = id("rename_anvil_item");
        public static final StreamCodec<RegistryFriendlyByteBuf, Rename> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Rename::containerId, ByteBufCodecs.stringUtf8(50), Rename::name, Rename::new);
        @Override public Type<Rename> type() { return TYPE; }
    }

    public record State(int containerId, Optional<ResourceLocation> selectedRecipe, int anvilCost)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = id("workstation_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, State::containerId, ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
                State::selectedRecipe, ByteBufCodecs.VAR_INT, State::anvilCost, State::new);
        @Override public Type<State> type() { return TYPE; }
    }
}
