package io.groovybot.bot.commands.music;


import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import io.groovybot.bot.GroovyBot;
import io.groovybot.bot.core.audio.MusicPlayer;
import io.groovybot.bot.core.command.CommandCategory;
import io.groovybot.bot.core.command.CommandEvent;
import io.groovybot.bot.core.command.Result;
import io.groovybot.bot.core.command.interaction.InteractableMessage;
import io.groovybot.bot.core.command.permission.Permissions;
import io.groovybot.bot.core.command.voice.SameChannelCommand;
import io.groovybot.bot.util.*;
import lavalink.client.player.IPlayer;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ControlCommand extends SameChannelCommand {

    private final String[] EMOTES = {"⏯", "⏭", "🔂", "🔁", "🔀", "🔄", "🔉", "🔊"};

    public ControlCommand() {
        super(new String[]{"control", "panel", "cp"}, CommandCategory.MUSIC, Permissions.everyone(), "Lets you control the bot with reactions", "");
    }

    @Override
    public Result runCommand(String[] args, CommandEvent event) {
        if (!event.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MANAGE))
            return send(error(event.translate("phrases.nopermission.title"), event.translate("phrases.nopermission.manage")));
        if (controlPanelExists(event.getGuild().getIdLong())) {
            Message confirmMessage = sendMessageBlocking(event.getChannel(), info(event.translate("command.control.alreadyinuse.title"), event.translate("command.control.alreadyinuse.description")));
            confirmMessage.addReaction("✅").queue();
            confirmMessage.addReaction("❌").queue();
            event.getGroovyBot().getEventWaiter().waitForEvent(GuildMessageReactionAddEvent.class, e -> confirmMessage.getIdLong() == e.getMessageIdLong() && e.getGuild().equals(event.getGuild()) && !e.getUser().isBot(),
                    e -> {
                        if (e.getReactionEmote().getName().equals("✅")) {
                            ControlPanel panel = getControlPanel(event.getGuild().getIdLong());
                            if (!panel.isWhitelisted(e.getMember())) {
                                sendMessage(e.getChannel(), error(event.translate("command.control.alreadyinuse.nopermission.title"), event.translate("command.control.alreadyinuse.nopermission.description")), 5);
                                return;
                            } else {
                                new Thread(() -> new ControlPanel(sendInfoMessage(event), event.getChannel(), event.getMember(), getPlayer(event.getGuild(), event.getChannel())), "ControlPanel").start();
                                panel.delete();
                            }
                        }
                        confirmMessage.delete().queue();
                    });
        } else
            new Thread(() -> new ControlPanel(sendInfoMessage(event), event.getChannel(), event.getMember(), getPlayer(event.getGuild(), event.getChannel())), "ControlPanel").start();
        return null;
    }
    
    private Message sendInfoMessage(CommandEvent event) {
        return sendMessageBlocking(event.getChannel(), info(event.translate("command.control.loading.title"), event.translate("command.control.loading.description")));
    }

    private List<InteractableMessage> getControlPanels() {
        return GroovyBot.getInstance().getInteractionManager().getInteractionStorage().values().stream().filter(entry -> entry instanceof ControlPanel).collect(Collectors.toList());
    }

    private boolean controlPanelExists(Long guildId) {
        return !getControlPanels().stream().filter(entry -> entry.getChannel().getGuild().getIdLong() ==guildId).collect(Collectors.toList()).isEmpty();
    }

    private ControlPanel getControlPanel(Long guildId) {
        return (ControlPanel) getControlPanels().stream().filter(entry -> entry.getChannel().getGuild().getIdLong() ==guildId).collect(Collectors.toList()).get(0);
    }

    private class ControlPanel extends InteractableMessage implements Runnable {

        private final VoiceChannel channel;
        private final ScheduledExecutorService scheduler;
        private final MusicPlayer player;

        public ControlPanel(Message infoMessage, TextChannel channel, Member author, MusicPlayer player) {
            super(infoMessage, channel, author);
            this.channel = author.getGuild().getSelfMember().getVoiceState().getChannel();
            this.player = player;
            this.scheduler = Executors.newScheduledThreadPool(1, new NameThreadFactory("ControlPanel"));
            for (String emote : EMOTES) {
                getInfoMessage().addReaction(emote).complete();
            }
            run();
            scheduler.scheduleAtFixedRate(this, 0, 10, TimeUnit.SECONDS);
        }

        @Override
        protected void handleReaction(GuildMessageReactionAddEvent event) {
            if (!isWhitelisted(event.getMember()))
                return;
            final IPlayer musicPlayer = this.player.getPlayer();
            switch (event.getReaction().getReactionEmote().getName()) {
                case "⏯":
                    if (!player.isPaused()) {
                        musicPlayer.setPaused(true);
                        sendMessage(translate(event, "controlpanel.paused.title"), translate(event, "controlpanel.paused.description"));
                    } else {
                        musicPlayer.setPaused(false);
                        sendMessage(translate(event, "controlpanel.resumed.title"), translate(event, "controlpanel.resumed.description"));
                    }
                    break;
                case "⏭":
                    this.player.skip();
                    sendMessage(translate(event, "controlpanel.skipped.title"), translate(event, "controlpanel.skipped.description"));
                    break;
                case "\uD83D\uDD02":
                    //TODO: Implement loop
                    if (this.player.getScheduler().isRepeating()) {
                        this.player.getScheduler().setRepeating(true);
                        sendMessage(translate(event, "controlpanel.repeating.enabled.title"), translate(event, "controlpanel.repeating.enabled.description"));
                    } else {
                        this.player.getScheduler().setRepeating(false);
                        sendMessage(translate(event, "controlpanel.repeating.disabled.title"), translate(event, "controlpanel.repeating.disabled.description"));
                    }
                    break;
                case "\uD83D\uDD01":
                    //TODO: Implement queue loop
                    if (this.player.getScheduler().isQueueRepeating()) {
                        this.player.getScheduler().setQueueRepeating(true);
                        sendMessage(translate(event, "controlpanel.queuerepeating.enabled.title"), translate(event, "controlpanel.queuerepeating.enabled.description"));
                    } else {
                        this.player.getScheduler().setRepeating(false);
                        sendMessage(translate(event, "controlpanel.queuerepeating.disabled.title"), translate(event, "controlpanel.queuerepeating.disabled.description"));
                    }
                    break;
                case "\uD83D\uDD0A":
                    if (musicPlayer.getVolume() == 200) {
                        sendMessage(translate(event, "controlpanel.volume.tohigh.title"), translate(event, "controlpanel.volume.tohigh.description"));
                        return;
                    }
                    if (musicPlayer.getVolume() >= 190)
                        this.player.setVolume(200);
                    else
                        this.player.setVolume(musicPlayer.getVolume() + 10);
                    sendMessage(translate(event, "controlpanel.volume.increased.title"), String.format(translate(event, "controlpanel.volume.increased.description"), musicPlayer.getVolume()));
                    break;
                case "\uD83D\uDD09":
                    if (musicPlayer.getVolume() == 0) {
                        sendMessage(translate(event, "controlpanel.volume.tolow.title"), translate(event, "controlpanel.volume.tolow.description"));
                        return;
                    }
                    if (musicPlayer.getVolume() <= 10)
                        this.player.setVolume(0);
                    else
                        this.player.setVolume(musicPlayer.getVolume() - 10);
                    sendMessage(translate(event, "controlpanel.volume.decreased.title"), String.format(translate(event, "controlpanel.volume.decreased.description"), musicPlayer.getVolume()));
                    break;
                case "\uD83D\uDD04":
                    player.seekTo(0);
                    sendMessage(translate(event, "controlpanel.reset.title"), String.format(translate(event, "controlpanel.reset.description"), musicPlayer.getVolume()));
                    break;
                default:
                    break;
            }
            run();
        }

        private boolean isWhitelisted(Member member) {
            return channel.getMembers().contains(member);
        }

        @Override
        public void run() {
            if (!player.isPlaying())
                delete();
            if (player.getPlayer() == null || player.getPlayer().getPlayingTrack() == null)
                return;
            AudioTrackInfo currentSong = player.getPlayer().getPlayingTrack().getInfo();
            EmbedBuilder controlPanelEmbed = new EmbedBuilder()
                    .setTitle(String.format(":notes: %s (%s)", currentSong.title, currentSong.author))
                    .setColor(Colors.DARK_BUT_NOT_BLACK)
                    .setDescription(buildDescription(player));
            getInfoMessage().editMessage(controlPanelEmbed.build()).queue();
        }

        private CharSequence buildDescription(MusicPlayer player) {
            final AudioTrack playingTrack = player.getPlayer().getPlayingTrack();
            final long trackPosition = player.getPlayer().getTrackPosition();
            return String.format("%s%s%s %s **[%s/%s]**", player.isPaused() ? "\u23F8" : "\u25B6", player.loopEnabled() ? "\uD83D\uDD02" : "", player.queueLoopEnabled() ? "\uD83D\uDD01" : "", getProgressBar(trackPosition, playingTrack.getDuration()), FormatUtil.formatTimestamp(trackPosition), FormatUtil.formatTimestamp(playingTrack.getDuration()));
        }

        private void delete() {
            scheduler.shutdownNow();
            unregister();
            if (getInfoMessage() != null)
                getInfoMessage().delete().queue();
        }

        private void sendMessage(String title, String message) {
            SafeMessage.sendMessage(getChannel(), EmbedUtil.success(title, message), 4);
        }

        private String getProgressBar(long progress, long full) {
            double percentage = (double) progress / full;
            StringBuilder progressBar = new StringBuilder();
            for (int i = 0; i < 15; i++) {
                if ((int) (percentage * 15) == i)
                    progressBar.append("\uD83D\uDD18");
                else
                    progressBar.append("▬");
            }
            return progressBar.toString();
        }

        @Override
        public void onDelete() {
            if (getInfoMessage() != null)
                getInfoMessage().delete().queue();
            if (!scheduler.isShutdown())
                scheduler.shutdownNow();
        }

    }
}
