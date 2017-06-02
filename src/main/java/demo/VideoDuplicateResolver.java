/*
 * This file is part of Guru Cue Search & Recommendation Engine.
 * Copyright (C) 2017 Guru Cue Ltd.
 *
 * Guru Cue Search & Recommendation Engine is free software: you can
 * redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * Guru Cue Search & Recommendation Engine is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Guru Cue Search & Recommendation Engine. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package demo;

import com.gurucue.recommendations.blender.DuplicateResolver;
import com.gurucue.recommendations.blender.TvChannelData;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import com.gurucue.recommendations.entity.product.VideoProduct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Random;

/**
 * Decides which value to use among two duplicate videos.
 */
public final class VideoDuplicateResolver implements DuplicateResolver<VideoData> {
    private final boolean preferHD;
    private final long recommendTimestampMillis;
    private final Random rnd = new Random();

    private static final Logger log = LogManager.getLogger(VideoDuplicateResolver.class);

    public VideoDuplicateResolver(final boolean preferHD, final long recommendTimestampMillis) {
        this.preferHD = preferHD;
        this.recommendTimestampMillis = recommendTimestampMillis;
    }

    public VideoDuplicateResolver(final String requestedVideoFormat, final long recommendTimestampMillis) {
        this.preferHD = "HD".equalsIgnoreCase(requestedVideoFormat);
        this.recommendTimestampMillis = recommendTimestampMillis;
    }

    @Override
    public VideoData resolve(final VideoData value1, final VideoData value2) {
        if (value1 == value2) return value1; // they're exactly the same, so just use the first one
        if (value1.isSubscribed == value2.isSubscribed) {
            // both are either subscribed or not subscribed: look at the price
            final double price1 = getPrice(value1);
            final double price2 = getPrice(value2);
            if (price1 == price2) {
                // both cost (or don't cost) the same, see whether we prefer HD or not
                final boolean isHD1 = isHD(value1);
                final boolean isHD2 = isHD(value2);
                if (isHD1 == isHD2) {
                    // both are of the same video format, prefer the VOD as source
                    if (value1.isTvProgramme == value2.isTvProgramme) {
                        // both are from the same source, now see whether we are dealing with catch-up
                        if (!value1.isTvProgramme) {
                            // random choice: both sources are VOD
                            if (rnd.nextBoolean()) return value1;
                            return value2;
                        }
                        final TvProgrammeProduct tv1 = (TvProgrammeProduct) value1.video;
                        final TvProgrammeProduct tv2 = (TvProgrammeProduct) value2.video;
                        if (tv1.beginTimeMillis == tv2.beginTimeMillis) {
                            // random choice: both shows start at the same time
                            if (rnd.nextBoolean()) return value1;
                            return value2;
                        }

                        // prefer the show that starts earlier: the oldest one in catch-up, or the soonest one in live-tv
                        if (tv1.beginTimeMillis < tv2.beginTimeMillis) return value1;
                        return value2;
                    }

                    // prefer VOD as source
                    if (value1.isTvProgramme) return value2;
                    return value1;
                }

                // prefer the user's video-format
                if (isHD1 == preferHD) return value1;
                return value2;
            }

            // prefer the cheaper content
            if (price1 <= price2) return value1;
            return value2;
        }

        // prefer the subscribed content
        if (value1.isSubscribed) return value1;
        return value2;
    }

    private double getPrice(final VideoData data) {
        if (data.isTvProgramme) {
            if (data.isSubscribed) return 0.0;
            // TODO: we don't have information about subscription package pricing, so we use the maximum price
            return Double.MAX_VALUE;
        }
        final VideoProduct video = (VideoProduct)data.video;
        if (video.price > 0.0) return video.price;
        if (data.isSubscribed) return 0.0;
        // TODO: we don't have information about subscription package pricing, so we use the maximum price
        return Double.MAX_VALUE;
    }

    private boolean isHD(final String videoFormat) {
        return (videoFormat != null) && ("HD".equalsIgnoreCase(videoFormat));
    }

    private boolean isHD(final VideoData data) {
        if (data.isTvProgramme) {
            if (data.chosenTvChannels.isEmpty()) { // this is weird... no chosen TV-channels?
                data.chosenTvChannels.addAll(data.availableTvChannels);
                if (data.chosenTvChannels.isEmpty()) return false; // this is weird... no available TV-channels?
            }
            if (data.chosenTvChannels.size() == 1) {
                // this is faster in case there is only one TV-channel
                return isHD(data.chosenTvChannels.iterator().next().tvChannel.videoFormat);
            }

            // first see if there are TV-channels with different video-formats
            final Iterator<TvChannelData> it = data.chosenTvChannels.iterator();
            boolean allFormatsAreTheSame = true;
            final boolean firstVideoFormatIsHD = isHD(it.next().tvChannel.videoFormat);
            while (it.hasNext()) {
                if (firstVideoFormatIsHD != isHD(it.next().tvChannel.videoFormat)) {
                    allFormatsAreTheSame = false;
                    break;
                }
            }
            if (allFormatsAreTheSame) return firstVideoFormatIsHD;

            // sort out the preferred video-format from chosen TV-channels
            final Iterator<TvChannelData> it2 = data.chosenTvChannels.iterator();
            while (it2.hasNext()) {
                if (isHD(it2.next().tvChannel.videoFormat) != preferHD) {
                    it2.remove();
                }
            }
            return preferHD;
        }

        // this is a video
        return isHD(data.video.videoFormat);
    }
}
