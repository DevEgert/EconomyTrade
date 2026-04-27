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
            session.offeredByA = new ArrayList<>(items);
            if (session.confirmedA) {
                session.confirmedA = false;
                session.confirmedB = false;
                session.countingDown = false;
                session.countdownTimer = 0;
            }
        } else {
            session.offeredByB = new ArrayList<>(items);
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

    public static void executeTrade(ServerPlayer playerA, ServerPlayer playerB,
                                    TradeSession session) {
        for (ItemStack stack : session.offeredByA) {
            if (!playerB.getInventory().add(stack)) playerB.drop(stack, false);
        }
        for (ItemStack stack : session.offeredByB) {
            if (!playerA.getInventory().add(stack)) playerA.drop(stack, false);
        }
        cooldowns.put(playerA.getUUID(), TRADE_COOLDOWN_TICKS);
        cooldowns.put(playerB.getUUID(), TRADE_COOLDOWN_TICKS);
        sessions.remove(playerA.getUUID());
        sessions.remove(playerB.getUUID());
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
            executeTrade(pA, pB, session);
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