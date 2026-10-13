package me.myogoo.appliedterminalmodules.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.storage.ILinkStatus;
import appeng.menu.ISubMenu;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.myotus.menu.MyoSlotSemantics;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/** Real server players and terminal menus without requiring an ME network or client connection. */
public final class TestTerminals {
    private TestTerminals() {}

    public record Fixture(ServerPlayer player, MEStorageMenu menu, List<Slot> upgrades) {}

    public static Fixture create(GameTestHelper helper) {
        var player = player(helper);
        var menu = menu(helper, player);
        return new Fixture(player, menu, List.copyOf(menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT)));
    }

    public static void install(Fixture fixture, Item card) { install(fixture.menu(), card); }

    public static void remove(Fixture fixture) { cardSlot(fixture.menu()).set(ItemStack.EMPTY); }

    public static void broadcast(GameTestHelper helper, Fixture fixture) { broadcast(helper, fixture.menu()); }

    public static ServerPlayer player(GameTestHelper helper) {
        var level = helper.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "atm-test-player"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        if (ModList.get().isLoaded("accessories")) AccessoriesFixture.initialize(player);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie);
        return player;
    }

    private static final class AccessoriesFixture {
        private static void initialize(ServerPlayer player) {
            // First access resets the holder and sends a sync if a connection exists. Initialize
            // before attaching the unnegotiated embedded connection so menu factories can finish.
            io.wispforest.accessories.api.AccessoriesCapability.get(player);
        }
    }

    public static MEStorageMenu menu(GameTestHelper helper, ServerPlayer player) {
        return menu(helper, player, new TestHost(player));
    }

    public static MEStorageMenu menu(GameTestHelper helper, ServerPlayer player, ITerminalHost host) {
        var menu = new MEStorageMenu(MEStorageMenu.TYPE, 70, player.getInventory(), host);
        player.containerMenu = menu;
        embeddedPacket(helper, () -> menu.addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu container, int slot, ItemStack stack) {}

            @Override
            public void dataChanged(AbstractContainerMenu container, int id, int value) {}
        }));
        return menu;
    }

    public static Slot cardSlot(MEStorageMenu menu) {
        return menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT).getFirst();
    }

    public static void install(MEStorageMenu menu, Item card) {
        cardSlot(menu).set(new ItemStack(card));
    }

    public static void broadcast(GameTestHelper helper, MEStorageMenu menu) {
        embeddedPacket(helper, menu::broadcastChanges);
    }

    private static void embeddedPacket(GameTestHelper helper, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException error) {
            // An embedded connection has not negotiated AE2 payloads; lifecycle runs before the send.
            helper.assertTrue(error.getMessage() != null && error.getMessage().contains("may not be sent to the client"),
                    "Unexpected embedded-player error: " + error);
        }
    }

    static class TestHost extends ItemMenuHost<Item> implements ITerminalHost {
        private final IConfigManager config = IConfigManager.builder(() -> {}).build();
        private final MEStorage storage = () -> Component.literal("ATM GameTest empty storage");

        TestHost(Player player) {
            super(Items.STICK, player, MenuLocators.forStack(new ItemStack(Items.STICK)));
        }

        @Override
        public MEStorage getInventory() { return storage; }

        @Override
        public ILinkStatus getLinkStatus() { return ILinkStatus.ofDisconnected(); }

        @Override
        public IConfigManager getConfigManager() { return config; }

        @Override
        public void returnToMainMenu(Player player, ISubMenu subMenu) {}

        @Override
        public ItemStack getMainMenuIcon() { return ItemStack.EMPTY; }
    }
}
