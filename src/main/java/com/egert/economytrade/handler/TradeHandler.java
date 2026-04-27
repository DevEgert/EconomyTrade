package com.egert.economytrade.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.egert.economytrade.EconomyTrade;
import com.egert.economytrade.network.NetworkHandler;
import com.egert.economytrade.network.TradePacket;

import java.util.*;

@Mod.EventBusSubscriber(modid = EconomyTrade.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TradeHandler {

    private static final Map<UUID, TradeSession> sessions = new HashMap<>();
    private static final Map<UUID, Integer> cooldowns = new HashMap<>();
    private static final int TRADE_COOLDOWN_TICKS = 600; // 30 seconds
    private static final int REQUEST_TIMEOUT_TICKS = 600; // 30 seconds
    private static final int CONFIRM_COUNTDOWN_TICKS = 60; // 3 seconds
    private static final int MAX_TRADE_SLOTS = 12;

    public static class TradeSession {
        public UUID playerA;
        public UUID playerB;
        public List<ItemStack> offeredByA = new ArrayList<>();
        public List<ItemStack> offeredByB = new ArrayList<>();
        public boolean confirmedA = false;
        public boolean confirmedB = false;
        public boolean pending = false;
        public int requestTimer = REQUEST_TIMEOUT_TICKS;
        public boolean countingDown = false;
        public int countdownTimer = 0;

        public TradeSession(UUID playerA, UUID playerB, boolean pending) {
            this.playerA = playerA;
            this.playerB = playerB;
            this.pending = pending;
        }

        public boolean isPlayerA(UUID uuid) {
            return playerA.equals(uuid);
        }

        public UUID getPartnerOf(UUID uuid) {
            return isPlayerA(uuid) ? playerB : playerA;
        }

        public List<ItemStack> getOfferedBy(UUID uuid) {
            return isPlayerA(uuid) ? offeredByA : offeredByB;
        }

        public List<ItemStack> getOfferedByPartner(UUID uuid) {
            return isPlayerA(uuid) ? offeredByB : offeredByA;
        }

        public boolean bothConfirmed() {
            return confirmedA && confirmedB;
        }
    }

    public static boolean requestTrade(ServerPlayer sender, ServerPlayer target) {
        if (isInTrade(sender.getUUID()) || isInTrade(target.getUUID())) return false;

        if (cooldowns.containsKey(sender.getUUID())) {
            int remaining = cooldowns.get(sender.getUUID());
            sender.sendSystemMessage(Component.literal(
                    "§cYou must wait §e" + (remaining / 20) +
                            "§c seconds before trading again."));
            return false;
        }

        TradeSession session = new TradeSession(
                sender.getUUID(), target.getUUID(), true);
        sessions.put(target.getUUID(), session);
        sessions.put(sender.getUUID(), session);
        return true;
    }

    public static boolean acceptTrade(ServerPlayer player) {
        TradeSession session = sessions.get(player.getUUID());
        if (session == null || !session.pending) return false;
        session.pending = false;
        session.requestTimer = 0;
        return true;
    }

    public static void cancelTrade(UUID playerUUID) {
        TradeSession session = sessions.get(playerUUID);
        if (session == null) return;
        sessions.remove(session.playerA);
        sessions.remove(session.playerB);
    }

    public static TradeSession getSession(UUID playerUUID) {
        return sessions.get(playerUUID);
    }

    public static boolean isInTrade(UUID playerUUID) {
        return sessions.containsKey(playerUUID);
    }

    public static boolean isPending(UUID playerUUID) {
        TradeSession session = sessions.get(playerUUID);
        return session != null && session.pending;
    }

    public static void updateOffer(UUID playerUUID, List<ItemStack> items) {
        TradeSession session = sessions.get(playerUUID);
        if (session == null) return;

        if (session.isPlayerA(playerUUID)) {
            session.offeredByA = sanitizeOffer(items);
            if (session.confirmedA) {
                session.confirmedA = false;
                session.confirmedB = false;
                session.countingDown = false;
                session.countdownTimer = 0;
            }
        } else {
            session.offeredByB = sanitizeOffer(items);
            if (session.confirmedB) {
                session.confirmedA = false;
                session.confirmedB = false;
                session.countingDown = false;
                session.countdownTimer = 0;
            }
        }
    }

    public static boolean confirmTrade(UUID playerUUID) {
        TradeSession session = sessions.get(playerUUID);
        if (session == null) return false;
        if (session.isPlayerA(playerUUID)) {
            session.confirmedA = true;
        } else {
            session.confirmedB = true;
        }
        if (session.bothConfirmed() && !session.countingDown) {
            session.countingDown = true;
            session.countdownTimer = CONFIRM_COUNTDOWN_TICKS;
        }
        return false;
    }

    public static boolean executeTrade(ServerPlayer playerA, ServerPlayer playerB,
                                       TradeSession session) {
        List<ItemStack> offerA = sanitizeOffer(session.offeredByA);
        List<ItemStack> offerB = sanitizeOffer(session.offeredByB);
        boolean countsForProgress = !offerA.isEmpty() && !offerB.isEmpty();

        if (!hasItems(playerA, offerA) || !hasItems(playerB, offerB)) {
            playerA.sendSystemMessage(Component.literal(
                    "§cTrade cancelled: offered items are no longer available."));
            playerB.sendSystemMessage(Component.literal(
                    "§cTrade cancelled: offered items are no longer available."));
            cooldowns.put(playerA.getUUID(), 100);
            cooldowns.put(playerB.getUUID(), 100);
            sessions.remove(playerA.getUUID());
            sessions.remove(playerB.getUUID());
            return false;
        }

        removeItems(playerA, offerA);
        removeItems(playerB, offerB);

        for (ItemStack stack : offerA) {
            ItemStack copy = stack.copy();
            if (!playerB.getInventory().add(copy)) playerB.drop(copy, false);
        }
        for (ItemStack stack : offerB) {
            ItemStack copy = stack.copy();
            if (!playerA.getInventory().add(copy)) playerA.drop(copy, false);
        }
        cooldowns.put(playerA.getUUID(), TRADE_COOLDOWN_TICKS);
        cooldowns.put(playerB.getUUID(), TRADE_COOLDOWN_TICKS);
        if (countsForProgress) {
            incrementTradeCounts(playerA);
            incrementTradeCounts(playerB);
        }
        sessions.remove(playerA.getUUID());
        sessions.remove(playerB.getUUID());
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Set<TradeSession> processed = new HashSet<>();
        List<TradeSession> toCancel = new ArrayList<>();
        List<TradeSession> toExecute = new ArrayList<>();

        for (TradeSession session : sessions.values()) {
            if (processed.contains(session)) continue;
            processed.add(session);

            if (session.pending) {
                session.requestTimer--;
                if (session.requestTimer <= 0) {
                    toCancel.add(session);
                    continue;
                }
                if (session.requestTimer == 200) {
                    ServerPlayer target = event.getServer()
                            .getPlayerList().getPlayer(session.playerB);
                    if (target != null) {
                        target.sendSystemMessage(Component.literal(
                                "§eTrade request expires in 10 seconds! " +
                                        "Type §f/trade accept §eor §f/trade decline§e."));
                    }
                }
                continue;
            }

            if (session.countingDown) {
                session.countdownTimer--;

                ServerPlayer pA = event.getServer()
                        .getPlayerList().getPlayer(session.playerA);
                ServerPlayer pB = event.getServer()
                        .getPlayerList().getPlayer(session.playerB);

                if (session.countdownTimer == 40) {
                    if (pA != null) pA.sendSystemMessage(
                            Component.literal("§aTrade completing in §e3§a..."));
                    if (pB != null) pB.sendSystemMessage(
                            Component.literal("§aTrade completing in §e3§a..."));
                } else if (session.countdownTimer == 20) {
                    if (pA != null) pA.sendSystemMessage(
                            Component.literal("§aTrade completing in §e2§a..."));
                    if (pB != null) pB.sendSystemMessage(
                            Component.literal("§aTrade completing in §e2§a..."));
                } else if (session.countdownTimer == 1) {
                    if (pA != null) pA.sendSystemMessage(
                            Component.literal("§aTrade completing in §e1§a..."));
                    if (pB != null) pB.sendSystemMessage(
                            Component.literal("§aTrade completing in §e1§a..."));
                }

                if (session.countdownTimer <= 0) {
                    if (pA != null && pB != null) {
                        toExecute.add(session);
                    } else {
                        toCancel.add(session);
                    }
                }
            }
        }

        // Tick cooldowns
        List<UUID> expiredCooldowns = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : cooldowns.entrySet()) {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                expiredCooldowns.add(entry.getKey());
            } else {
                cooldowns.put(entry.getKey(), remaining);
            }
        }
        expiredCooldowns.forEach(cooldowns::remove);

        // Execute completed trades
        for (TradeSession session : toExecute) {
            ServerPlayer pA = event.getServer()
                    .getPlayerList().getPlayer(session.playerA);
            ServerPlayer pB = event.getServer()
                    .getPlayerList().getPlayer(session.playerB);
            if (pA == null || pB == null) {
                cancelAndNotify(session, event);
                continue;
            }
            if (!executeTrade(pA, pB, session)) {
                NetworkHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor
                                .PLAYER.with(() -> pA),
                        new TradePacket(TradePacket.Action.CANCEL, "INVALID_ITEMS")
                );
                NetworkHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor
                                .PLAYER.with(() -> pB),
                        new TradePacket(TradePacket.Action.CANCEL, "INVALID_ITEMS")
                );
                continue;
            }
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor
                            .PLAYER.with(() -> pA),
                    new TradePacket(TradePacket.Action.COMPLETE, "TRADE_COMPLETE")
            );
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor
                            .PLAYER.with(() -> pB),
                    new TradePacket(TradePacket.Action.COMPLETE, "TRADE_COMPLETE")
            );
        }

        // Cancel timed out trades
        for (TradeSession session : toCancel) {
            cancelAndNotify(session, event);
        }
    }

    private static List<ItemStack> sanitizeOffer(List<ItemStack> items) {
        List<ItemStack> sanitized = new ArrayList<>();
        if (items == null) return sanitized;

        for (ItemStack stack : items) {
            if (sanitized.size() >= MAX_TRADE_SLOTS) break;
            if (stack == null || stack.isEmpty()) continue;

            ItemStack copy = stack.copy();
            int maxCount = Math.min(copy.getMaxStackSize(), 64);
            if (copy.getCount() > maxCount) copy.setCount(maxCount);
            if (copy.getCount() > 0) sanitized.add(copy);
        }
        return sanitized;
    }

    private static boolean hasItems(ServerPlayer player, List<ItemStack> offered) {
        List<ItemStack> inventory = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            inventory.add(stack.copy());
        }

        for (ItemStack needed : offered) {
            int remaining = needed.getCount();
            for (ItemStack available : inventory) {
                if (!ItemStack.isSameItemSameTags(available, needed)) continue;
                int taken = Math.min(remaining, available.getCount());
                available.shrink(taken);
                remaining -= taken;
                if (remaining <= 0) break;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private static void removeItems(ServerPlayer player, List<ItemStack> offered) {
        for (ItemStack needed : offered) {
            int remaining = needed.getCount();
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack available = player.getInventory().items.get(i);
                if (!ItemStack.isSameItemSameTags(available, needed)) continue;

                int taken = Math.min(remaining, available.getCount());
                available.shrink(taken);
                if (available.isEmpty()) {
                    player.getInventory().items.set(i, ItemStack.EMPTY);
                }
                remaining -= taken;
                if (remaining <= 0) break;
            }
        }
        player.getInventory().setChanged();
    }

    private static void incrementTradeCounts(ServerPlayer player) {
        var data = player.getPersistentData();
        data.putInt("trade_count", data.getInt("trade_count") + 1);

        if (data.contains("player_role_2") && !data.getString("player_role_2").isEmpty()) {
            data.putInt("trade_count_2", data.getInt("trade_count_2") + 1);
        }
        if (data.contains("player_role_3") && !data.getString("player_role_3").isEmpty()) {
            data.putInt("trade_count_3", data.getInt("trade_count_3") + 1);
        }
    }

    private static void cancelAndNotify(TradeSession session,
                                        TickEvent.ServerTickEvent event) {
        ServerPlayer pA = event.getServer()
                .getPlayerList().getPlayer(session.playerA);
        ServerPlayer pB = event.getServer()
                .getPlayerList().getPlayer(session.playerB);

        if (pA != null) {
            if (session.pending) {
                pA.sendSystemMessage(Component.literal(
                        "§cTrade request expired."));
            } else {
                pA.sendSystemMessage(Component.literal(
                        "§cTrade cancelled — partner disconnected."));
            }
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor
                            .PLAYER.with(() -> pA),
                    new TradePacket(TradePacket.Action.CANCEL, "TIMEOUT")
            );
        }
        if (pB != null) {
            if (session.pending) {
                pB.sendSystemMessage(Component.literal(
                        "§cTrade request expired."));
            } else {
                pB.sendSystemMessage(Component.literal(
                        "§cTrade cancelled — partner disconnected."));
            }
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor
                            .PLAYER.with(() -> pB),
                    new TradePacket(TradePacket.Action.CANCEL, "TIMEOUT")
            );
        }

        // Short cooldown on cancel to prevent spam
        cooldowns.put(session.playerA, 100); // 5 seconds
        cooldowns.put(session.playerB, 100);

        sessions.remove(session.playerA);
        sessions.remove(session.playerB);
    }
}
