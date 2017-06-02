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

import com.gurucue.recommendations.blender.DataSet;
import com.gurucue.recommendations.blender.StatefulFilter;
import com.gurucue.recommendations.blender.VideoData;

import java.util.Comparator;

/**
 * Reorders items of the DataSet so they are sorted in ascending order
 * according to season and episode number.
 */
public final class EpisodeSorter implements StatefulFilter<VideoData> {
    private static final EpisodeComparator episodeComparator = new EpisodeComparator();

    @Override
    public DataSet<VideoData> transform(final DataSet<VideoData> source) {
        final DataSet<VideoData> result = new DataSet<>(source);
        result.sort(episodeComparator);
        return result;
    }

    private static final class EpisodeComparator implements Comparator<VideoData> {

        @Override
        public int compare(final VideoData o1, final VideoData o2) {
            if (o1.video.seriesId <= 0L) return -1;
            if (o2.video.seriesId <= 0L) return 1;

            // are we comparing by air-date?
            if (o1.video.airDate > 0L) {
                if (o2.video.airDate > 0L) {
                    if (o1.video.airDate < o2.video.airDate) return -1;
                    if (o1.video.airDate > o2.video.airDate) return 1;
                    return 0;
                }
                return 1; // o2 has a NULL airDate, nulls go first
            }
            else if (o2.video.airDate > 0L) return -1; // o1 has a NULL airDate, nulls go first

            // we are comparing season-number and episode-number
            final long seasonNumber1 = o1.video.seasonNumber < 1L ? 1L : o1.video.seasonNumber;
            final long seasonNumber2 = o2.video.seasonNumber < 1L ? 1L : o2.video.seasonNumber;
            if (seasonNumber1 < seasonNumber2) return -1;
            if (seasonNumber1 > seasonNumber2) return 1;

            final long episodeNumber1 = o1.video.episodeNumber < 1L ? 1L : o1.video.episodeNumber;
            final long episodeNumber2 = o2.video.episodeNumber < 1L ? 1L : o2.video.episodeNumber;
            if (episodeNumber1 < episodeNumber2) return -1;
            if (episodeNumber1 > episodeNumber2) return 1;

            return 0;
        }
    }
}
