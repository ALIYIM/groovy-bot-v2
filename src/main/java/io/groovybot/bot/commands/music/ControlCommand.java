package io.groovybot.bot.commands.music;


import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import io.groovybot.bot.GroovyBot;
import io.groovybot.bot.core.audio.MusicPlayer;
import io.groovybot.bot.core.audio.Scheduler;
import io.groovybot.bot.core.command.CommandCategory;
import io.groovybot.bot.core.command.CommandEvent;
import io.groovybot.bot.core.command.Result;
import io.groovybot.bot.core.command.interaction.InteractableMessage;
import io.groovybot.bot.core.command.permission.Permissions;
import io.groovybot.bot.core.command.voice.SameChannelCommand;
import io.groovybot.bot.core.entity.EntityProvider;
import io.groovybot.bot.util.*;
import lavalink.client.player.IPlayer;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ControlCommand extends SameChannelCommand {

    private final String[] EMOTES = {"⏯", "⏭", "🔁", "🔀", "🔄", "🔉", "🔊"};

    public ControlCommand() {
        super(new String[]{"control", "panel", "cp"}, CommandCategory.MUSIC, Permissions.djMode(), "Lets you control Groovy with reactions", "");
    }

    @Override
    public Result runCommand(String[] args, CommandEvent event, MusicPlayer player) {
        if (!event.getGuild().getSelfMember().hasPermission(event.getChannel(), Permission.MESSAGE_MANAGE))
            return send(error(event.translate("phrases.nopermission.title"), event.translate("phrases.nopermission.manage")));
        if (controlPanelExists(event.getGuild().getIdLong())) {
            Message confirmMessage = sendMessageBlocking(event.getChannel(), info(event.translate("command.control.alreadyinuse.title"), event.translate("command.control.alreadyinuse.description")));
            confirmMessage.addReaction("✅").queue();
            confirmMessage.addReaction("❌").queue();
            event.getBot().getEventWaiter().waitForEvent(GuildMessageReactionAddEvent.class, e -> confirmMessage.getIdLong() == e.getMessageIdLong() && e.getGuild().equals(event.getGuild()) && !e.getUser().isBot(),
                    e -> {
                        if (e.getReactionEmote().getName().equals("✅")) {
                            ControlPanel panel = getControlPanel(event.getGuild().getIdLong());
                            if (!panel.isWhitelisted(e.getMember())) {
                                sendMessage(e.getChannel(), error(event.translate("command.control.alreadyinuse.nopermission.title"), event.translate("command.control.alreadyinuse.nopermission.description")), 5);
                                return;
                            } else {
                                new ControlPanel(event, sendInfoMessage(event), event.getChannel(), event.getMember(), player);
                                panel.delete();
                            }
                        }
                        confirmMessage.delete().queue();
                    });
        } else
            new Thread(() -> new ControlPanel(event, sendInfoMessage(event), event.getChannel(), event.getMember(), player), "command.control").start();
        return null;
    }

    private Message sendInfoMessage(CommandEvent event) {
        return sendMessageBlocking(event.getChannel(), info(event.translate("command.control.loading.title"), event.translate("command.control.loading.description")));
    }

    private List<InteractableMessage> getControlPanels() {
        return GroovyBot.getInstance().getInteractionManager().getInteractionStorage().values().stream().filter(entry -> entry instanceof ControlPanel).collect(Collectors.toList());
    }

    private boolean controlPanelExists(Long guildId) {
        return !getControlPanels().stream().filter(entry -> entry.getChannel().getGuild().getIdLong() == guildId).collect(Collectors.toList()).isEmpty();
    }

    private ControlPanel getControlPanel(Long guildId) {
        return (ControlPanel) getControlPanels().stream().filter(entry -> entry.getChannel().getGuild().getIdLong() == guildId).collect(Collectors.toList()).get(0);
    }

    private class ControlPanel extends InteractableMessage implements Runnable {

        private final CommandEvent commandEvent;
        private final VoiceChannel channel;
        private final ScheduledExecutorService scheduler;
        private final MusicPlayer player;
        private boolean ready;

        public ControlPanel(CommandEvent commandEvent, Message infoMessage, TextChannel channel, Member author, MusicPlayer player) {
            super(infoMessage, channel, author, infoMessage.getIdLong());
            this.commandEvent = commandEvent;
            this.channel = author.getGuild().getSelfMember().getVoiceState().getChannel();
            this.player = player;
            this.scheduler = Executors.newScheduledThreadPool(1, new NameThreadFactory("command.control"));
            for (String emote : EMOTES) {
                waitForEntity(getInfoMessage().addReaction(emote));
            }
            ready = true;
            run();
            scheduler.scheduleAtFixedRate(this, 0, 5, TimeUnit.SECONDS);
        }

        @Override
        protected void handleReaction(GuildMessageReactionAddEvent event) {
            if (!ready)
                return;
            final User author = event.getUser();
            if (!isWhitelisted(event.getMember()))
                return;
            final IPlayer musicPlayer = this.player.getPlayer();
            final Scheduler playerScheduler = this.player.getScheduler();
            switch (event.getReaction().getReactionEmote().getName()) {
                case "⏯":
                    if (!player.isPaused()) {
                        musicPlayer.setPaused(true);
                        sendMessage(translate(author, "command.control.paused.title"), translate(author, "command.control.paused.description"));
                    } else {
                        musicPlayer.setPaused(false);
                        sendMessage(translate(author, "command.control.resumed.title"), translate(author, "command.control.resumed.description"));
                    }
                    break;
                case "⏭":
                    this.player.skip();
                    sendMessage(translate(author, "command.control.skipped.title"), translate(author, "command.control.skipped.description"));
                    break;
                case "\uD83D\uDD02":
                    if (!playerScheduler.isLoop() && !playerScheduler.isLoopqueue()) {
                        playerScheduler.setLoop(true);
                        sendMessage(this.commandEvent.translate("command.loop.loop.title"), this.commandEvent.translate("command.loop.loop.description"));
                    } else if (playerScheduler.isLoop()) {
                        if (!Permissions.tierOne().isCovered(EntityProvider.getUser(this.commandEvent.getAuthor().getIdLong()).getPermissions(), this.commandEvent)) {
                            sendMessageError(this.commandEvent.translate("phrases.nopermission.title"), this.commandEvent.translate("phrases.nopermission.tierone"));
                            sendMessage(this.commandEvent.translate("command.loop.none.title"), this.commandEvent.translate("command.loop.none.description"));
                        } else {
                            playerScheduler.setLoop(false);
                            playerScheduler.setLoopqueue(true);
                            sendMessage(this.commandEvent.translate("command.loop.loopqueue.title"), this.commandEvent.translate("command.loop.loopqueue.description"));
                        }
                    } else if (playerScheduler.isLoopqueue()) {
                        playerScheduler.setLoopqueue(false);
                        sendMessage(this.commandEvent.translate("command.loop.none.title"), this.commandEvent.translate("command.loop.none.description"));
                    } else {
                        send(error(this.commandEvent));
                    }
                    break;
                case "\uD83D\uDD00":
                    if (!Permissions.tierTwo().isCovered(EntityProvider.getUser(author.getIdLong()).getPermissions(), this.commandEvent)) {
                        sendMessageError(this.commandEvent.translate("phrases.nopermission.title"), this.commandEvent.translate("phrases.nopermission.tiertwo"));
                        break;
                    }
                    if (!playerScheduler.isShuffle()) {
                        playerScheduler.setShuffle(true);
                        sendMessage(translate(author, "command.control.shuffle.enabled.title"), translate(author, "command.control.shuffle.enabled.description"));
                    } else {
                        playerScheduler.setShuffle(false);
                        sendMessage(translate(author, "command.control.shuffle.disabled.title"), translate(author, "command.control.shuffle.disabled.description"));
                    }
                    break;
                case "\uD83D\uDD0A":
                    if (musicPlayer.getVolume() == 200) {
                        sendMessage(translate(author, "command.control.volume.tohigh.title"), translate(author, "command.control.volume.tohigh.description"));
                        return;
                    }
                    if (musicPlayer.getVolume() >= 190)
                        this.player.setVolume(200);
                    else
                        this.player.setVolume(musicPlayer.getVolume() + 10);
                    sendMessage(translate(author, "command.control.volume.increased.title"), String.format(translate(author, "command.control.volume.increased.description"), musicPlayer.getVolume()));
                    break;
                case "\uD83D\uDD09":
                    if (musicPlayer.getVolume() == 0) {
                        sendMessage(translate(author, "command.control.volume.tolow.title"), translate(author, "command.control.volume.tolow.description"));
                        return;
                    }
                    if (musicPlayer.getVolume() <= 10)
                        this.player.setVolume(0);
                    else
                        this.player.setVolume(musicPlayer.getVolume() - 10);
                    sendMessage(translate(author, "command.control.volume.decreased.title"), String.format(translate(author, "command.control.volume.decreased.description"), musicPlayer.getVolume()));
                    break;
                case "\uD83D\uDD04":
                    player.seekTo(0);
                    sendMessage(translate(author, "command.control.reset.title"), String.format(translate(author, "command.control.reset.description"), musicPlayer.getVolume()));
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
                    .setTitle(String.format("🎶 %s (%s)", currentSong.title, currentSong.author))
                    .setColor(Colors.DARK_BUT_NOT_BLACK)
                    .setDescription(buildDescription(player));
            editMessage(getInfoMessage(), controlPanelEmbed);
        }

        private CharSequence buildDescription(MusicPlayer player) {
            final AudioTrack playingTrack = player.getPlayer().getPlayingTrack();
            final long trackPosition = player.getPlayer().getTrackPosition();
            return String.format("%s %s %s %s %s **[%s]** 🔊 **%s**", player.isPaused() ? "\u23F8" : "\u25B6", player.loopEnabled() ? "\uD83D\uDD02" : "", player.queueLoopEnabled() ? "\uD83D\uDD01" : "", player.shuffleEnabled() ? "\uD83D\uDD00" : "", getProgressBar(trackPosition, playingTrack.getDuration()), playingTrack.getInfo().isStream ? commandEvent.translate("phrases.stream") : String.format("%s/%s", FormatUtil.formatTimestamp(trackPosition), FormatUtil.formatTimestamp(playingTrack.getDuration())), player.getPlayer().getVolume() + "%");
        }

        private void delete() {
            scheduler.shutdownNow();
            unregister();
            if (getInfoMessage() != null)
                getInfoMessage().delete().queue();
        }

        private void sendMessage(String title, String message) {
            SafeMessage.sendMessage(getChannel(), EmbedUtil.success(title, message), 3);
        }

        private void sendMessageError(String title, String message) {
            SafeMessage.sendMessage(getChannel(), EmbedUtil.error(title, message), 3);
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
