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

import com.google.common.collect.ImmutableSet;
import com.gurucue.recommendations.blender.StatelessFilter;
import com.gurucue.recommendations.blender.VideoData;

/**
 * Passes through general video products (video and tv-programme) containing
 * any of the specified genres.
 * For a product to pass through it must contain a genre from
 * the set of genres.
 */
public final class GenreWhitelist implements StatelessFilter<VideoData> {
    final ImmutableSet<String> genres;

    public GenreWhitelist(final ImmutableSet<String> genres) {
        this.genres = genres;
    }

    int countWhitelisted = 0;
    int countRejected = 0;

    @Override
    public boolean test(final VideoData videoData) {
        final String[] g = videoData.video.genres;
        if (g == null) {
            countRejected++;
            return false;
        }

		for (int i = g.length - 1; i >= 0; i--) {
			for (final String value : genres) {
				if (g[i].startsWith(value)) {
					countWhitelisted++;
					return true;
				}			
			}
        }
		
        countRejected++;
        return false;
    }

    @Override
    public void writeLog(final StringBuilder output) {
        final String className = getClass().getSimpleName();
        output.append(className).append(" rejected: ").append(countRejected).append("\n");
        output.append(className).append(" accepted: ").append(countWhitelisted).append("\n");
    }
}
