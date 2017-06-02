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
package demo.filters;

import com.gurucue.recommendations.blender.StatelessFilter;
import com.gurucue.recommendations.blender.VideoData;

/**
 * Accept only content having the pre-defined series-id. Pass through
 * only episodes of a certain series and reject everything else.
 */
public final class AcceptOnlyEpisodesOfSeries implements StatelessFilter<VideoData> {
    private final long seriesId;

    public AcceptOnlyEpisodesOfSeries(final long seriesId) {
        this.seriesId = seriesId;
    }

    int countRejected = 0;
    int countAccepted = 0;

    @Override
    public boolean test(final VideoData videoData) {
        if (videoData.video.seriesId == seriesId) {
            countAccepted++;
            return true;
        }
        countRejected++;
        return false;
    }

    @Override
    public void writeLog(final StringBuilder output) {
        final String className = getClass().getSimpleName();
        output.append(className).append("(").append(seriesId).append(") rejected: ").append(countRejected).append("\n");
        output.append(className).append("(").append(seriesId).append(") accepted: ").append(countAccepted).append("\n");
    }
}
