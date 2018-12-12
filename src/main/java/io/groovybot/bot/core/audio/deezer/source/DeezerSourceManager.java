package io.groovybot.bot.core.audio.deezer.source;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.zeloon.deezer.client.DeezerClient;
import com.zeloon.deezer.domain.Track;
import com.zeloon.deezer.domain.internal.TrackId;
import com.zeloon.deezer.io.HttpResourceConnection;
import io.groovybot.bot.core.audio.AudioTrackFactory;
import io.groovybot.bot.core.audio.spotify.entities.track.TrackData;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.DataInput;
import java.io.DataOutput;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class DeezerSourceManager implements AudioSourceManager {

    @Getter
    private final DeezerClient deezerClient;
    @Getter
    private final AudioTrackFactory audioTrackFactory;

    private static final Pattern TRACK_PATTERN = Pattern.compile("https?://.*\\.deezer\\.com/.*/track/([0-9]*)");

    public DeezerSourceManager() {
        this.deezerClient = new DeezerClient(new HttpResourceConnection());
        this.audioTrackFactory = new AudioTrackFactory();
    }

    @Override
    public String getSourceName() {
        return "Deezer Source Manager";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        if (reference.identifier.startsWith("ytsearch:") || reference.identifier.startsWith("scsearch:")) return null;
        try {
            URL url = new URL(reference.identifier);
            if (!url.getHost().equalsIgnoreCase("www.deezer.com"))
                return null;
            String rawUrl = url.toString();
            AudioItem audioItem = null;

            if (TRACK_PATTERN.matcher(rawUrl).matches())
                audioItem = buildTrack(rawUrl);

            return audioItem;
        } catch (MalformedURLException e) {
            log.error("Failed to load the item!", e);
            return null;
        }
    }

    private AudioTrack buildTrack(String url) {
        TrackId trackId = new TrackId(Long.valueOf(parseTrackPattern(url)));
        Track track = this.deezerClient.get(trackId);
        TrackData trackData = this.getTrackData(track);
        return this.audioTrackFactory.getAudioTrack(trackData);
    }

    private TrackData getTrackData(Track track) {
        return new TrackData(
                track.getTitle(),
                track.getLink(),
                Collections.singletonList(track.getArtist().getName()),
                track.getDuration()
        );
    }

    private String parseTrackPattern(String identifier) {
        final Matcher matcher = TRACK_PATTERN.matcher(identifier);

        if (!matcher.find())
            return "noTrackId";
        return matcher.group(1);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack audioTrack) {
        return false;
    }

    @Override
    public void encodeTrack(AudioTrack audioTrack, DataOutput dataOutput) {
        throw new UnsupportedOperationException("encodeTrack is unsupported.");
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo audioTrackInfo, DataInput dataInput) {
        throw new UnsupportedOperationException("decodeTrack is unsupported.");
    }

    @Override
    public void shutdown() {
    }
}
