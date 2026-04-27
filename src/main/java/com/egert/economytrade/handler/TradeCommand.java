package com.egert.economytrade.handler;

import java.util.UUID;
import com.egert.economytrade.EconomyTrade;
import com.egert.economytrade.network.NetworkHandler;
import com.egert.economytrade.network.TradePacket;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = EconomyTrade.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TradeCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("trade")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .executes(ctx -> requestTrade(ctx,
                                        StringArgumentType.getString(ctx, "player"))))
                        .then(Commands.literal("accept")
                                .executes(TradeCommand::acceptTrade))
                        .then(Commands.literal("decline")
                                .executes(TradeCommand::declineTrade))
                        .then(Commands.literal("cancel")
                                .executes(TradeCommand::cancelTrade))
                        .then(Commands.literal("test")
                                .executes(ctx -> {
                                    try {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        NetworkHandler.CHANNEL.send(
                                                PacketDistributor.PLAYER.with(() -> player),
                                                new TradePacket(TradePacket.Action.ACCEPT,
                                                        player.getName().getString())
                                        );
                                        return 1;
                                    } catch (Exception e) { return 0; }
                                }))
        );
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        if (!(event.getEntity() instanceof ServerPlayer sender)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!sender.getMainHandItem().isEmpty()) return;

        if (TradeHandler.isInTrade(sender.getUUID())) {
            sender.sendSystemMessage(Component.literal(
                    "§cYou are already in a trade. Use /trade cancel first."));
            event.setCanceled(true);
            return;
        }

        if (TradeHandler.isInTrade(target.getUUID())) {
            sender.sendSystemMessage(Component.literal(
                    "§c" + target.getName().getString() + " is already in a trade."));
            event.setCanceled(true);
            return;
        }

        if (sender.distanceTo(target) > 10.0) {
            sender.sendSystemMessage(Component.literal(
                    "§cYou must be within 10 blocks to trade."));
            event.setCanceled(true);
            return;
        }

        TradeHandler.requestTrade(sender, target);

        sender.sendSystemMessage(Component.literal(
                "§aTrade request sent to §e" + target.getName().getString() + "§a."));
        target.sendSystemMessage(Component.literal(
                "§e" + sender.getName().getString() + " §awants to trade!"));
        target.sendSystemMessage(Component.literal(
                "§7Type §f/trade accept §7or §f/trade decline§7."));

        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> sender),
                new TradePacket(TradePacket.Action.REQUEST,
                        target.getName().getString())
        );

        event.setCanceled(true);
    }

    private static int requestTrade(CommandContext<CommandSourceStack> ctx,
                                    String targetName) {
        try {
            ServerPlayer sender = ctx.getSource().getPlayerOrException();
            ServerPlayer target = ctx.getSource().getServer()
                    .getPlayerList().getPlayerByName(targetName);

            if (target == null) {
                sender.sendSystemMessage(Component.literal(
                        "§cPlayer '" + targetName + "' is not online.")); return 0;
            }
            if (target == sender) {
                sender.sendSystemMessage(Component.literal(
                        "§cYou cannot trade with yourself.")); return 0;
            }
            if (TradeHandler.isInTrade(sender.getUUID())) {
                sender.sendSystemMessage(Component.literal(
                        "§cYou are already in a trade.")); return 0;
            }
            if (TradeHandler.isInTrade(target.getUUID())) {
                sender.sendSystemMessage(Component.literal(
                        "§c" + targetName + " is already in a trade.")); return 0;
            }

            double dist = sender.distanceTo(target);
            if (dist > 10.0) {
                sender.sendSystemMessage(Component.literal(
                        "§cYou must be within 10 blocks to trade. Distance: "
                                + String.format("%.1f", dist) + " blocks.")); return 0;
            }

            TradeHandler.requestTrade(sender, target);

            sender.sendSystemMessage(Component.literal(
                    "§aTrade request sent to §e" + targetName + "§a."));
            target.sendSystemMessage(Component.literal(
                    "§e" + sender.getName().getString()
                            + " §awants to trade with you!"));
            target.sendSystemMessage(Component.literal(
                    "§7Type §f/trade accept §7to open the trade window, "
                            + "or §f/trade decline §7to refuse."));

            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sender),
                    new TradePacket(TradePacket.Action.REQUEST, targetName)
            );
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int acceptTrade(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            if (!TradeHandler.isPending(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                        "§cYou have no pending trade request.")); return 0;
            }

            TradeHandler.acceptTrade(player);
            TradeHandler.TradeSession session =
                    TradeHandler.getSession(player.getUUID());
            ServerPlayer partner = ctx.getSource().getServer()
                    .getPlayerList().getPlayer(session.playerA);

            if (partner == null) {
                player.sendSystemMessage(Component.literal(
                        "§cThe other player is no longer online."));
                TradeHandler.cancelTrade(player.getUUID()); return 0;
            }

            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new TradePacket(TradePacket.Action.ACCEPT,
                            partner.getName().getString())
            );
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> partner),
                    new TradePacket(TradePacket.Action.ACCEPT,
                            player.getName().getString())
            );
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int declineTrade(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            TradeHandler.TradeSession session =
                    TradeHandler.getSession(player.getUUID());

            if (session == null) {
                player.sendSystemMessage(Component.literal(
                        "§cNo pending trade request.")); return 0;
            }

            ServerPlayer partner = ctx.getSource().getServer()
                    .getPlayerList().getPlayer(session.playerA);
            TradeHandler.cancelTrade(player.getUUID());

            player.sendSystemMessage(Component.literal("§cTrade declined."));
            if (partner != null) {
                partner.sendSystemMessage(Component.literal(
                        "§c" + player.getName().getString()
                                + " declined your trade request."));
            }
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int cancelTrade(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            TradeHandler.TradeSession session =
                    TradeHandler.getSession(player.getUUID());

            if (session == null) {
                player.sendSystemMessage(Component.literal(
                        "§cYou are not in a trade.")); return 0;
            }

            UUID partnerUUID = session.getPartnerOf(player.getUUID());
            ServerPlayer partner = ctx.getSource().getServer()
                    .getPlayerList().getPlayer(partnerUUID);

            TradeHandler.cancelTrade(player.getUUID());
            player.sendSystemMessage(Component.literal("§cTrade cancelled."));

            if (partner != null) {
                partner.sendSystemMessage(Component.literal(
                        "§c" + player.getName().getString()
                                + " cancelled the trade."));
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> partner),
                        new TradePacket(TradePacket.Action.CANCEL,
                                player.getName().getString())
                );
            }
            return 1;
        } catch (Exception e) { return 0; }
    }
}