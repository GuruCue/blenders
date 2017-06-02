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
package demo.recommenders;

import com.gurucue.recommendations.blender.BlendEnvironment;
import com.gurucue.recommendations.blender.BlendParameters;
import com.gurucue.recommendations.blender.BlenderResult;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.recommender.RecommendationSettings;
import com.google.common.collect.ImmutableSet;
import demo.BasicTagger;
import demo.DebugFiltersBuilder;
import demo.Utils;
import demo.VideoDuplicateResolver;
import demo.filters.MinimumRuntime;
import demo.filters.GenreWhitelist;

/**
 * Video blender for "movies" recommendations.
 */
public final class BlenderMovies implements RecommendBlender {

    public static final ImmutableSet<String> whiteGenres = ImmutableSet.copyOf(new String[] {
            "Movies"
    });

    @Override
    public BlenderResult<VideoData> blend(
            final BlendEnvironment environment,
            final BlendParameters parameters,
            int maxItems,
            final String requestedVideoFormat,
            final DebugFiltersBuilder debugFiltersBuilder
    ) {
        // set default output size, if not provided
        if (maxItems <= 0) maxItems = 20;

        // initialize and filter the DataSet
        return VideoData.buildDataSetOfVideosAndTvProgrammes(environment.transaction, environment.partner, environment.consumer, new VideoDuplicateResolver(requestedVideoFormat, environment.requestTimestampMillis), environment.requestTimestampMillis, Utils.MAX_CATCHUP_RETENTION, 0L)
                .filter(new MinimumRuntime(65)) // a movie has a minimum run-time of 65 minutes
                .filter(new GenreWhitelist(whiteGenres))
                .filter(new BasicTagger(environment.requestTimestampMillis, maxItems)) // this is an all-pass tagging filter
                .filter(debugFiltersBuilder.allDataLogger(environment.debug))
                .filter(environment.recommenderProvider.recommendationsFilter("demo-all", environment.consumer.id, new RecommendationSettings(maxItems, false)))
                .filter(debugFiltersBuilder.filteredDataLogger(environment.debug))
                .filter(debugFiltersBuilder.resultDataLogger(environment.debug))
                .result("all").feedback("recommender", "demo-all");
    }
}
