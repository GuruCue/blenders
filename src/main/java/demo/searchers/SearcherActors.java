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
package demo.searchers;

import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.blender.BlendEnvironment;
import com.gurucue.recommendations.blender.BlendParameters;
import com.gurucue.recommendations.blender.BlenderResult;
import com.gurucue.recommendations.blender.VideoData;
import demo.DebugFiltersBuilder;
import demo.Utils;
import demo.VideoDuplicateResolver;
import demo.filters.ActorsSearchFilter;
import demo.filters.RankedSizerObeyingTagRules;

/**
 * Restricted search: it applies all the marketing rules from the BlenderAll.
 */
public final class SearcherActors implements SearchBlender {
    @Override
    public BlenderResult<VideoData> blend(
            final BlendEnvironment environment,
            final BlendParameters parameters,
            int maxItems,
            final String requestedVideoFormat,
            final DebugFiltersBuilder debugFiltersBuilder,
            final String query
    ) throws ResponseException {
        // set default output size, if not provided
        if (maxItems <= 0) maxItems = 20;

        // initialize and filter the DataSet
        return VideoData.buildDataSetOfVideosAndTvProgrammes(environment.transaction, environment.partner, environment.consumer, new VideoDuplicateResolver(requestedVideoFormat, environment.requestTimestampMillis), environment.requestTimestampMillis, Utils.MAX_CATCHUP_RETENTION, 0L)
                .filter(debugFiltersBuilder.allDataLogger(environment.debug))
                .filter(debugFiltersBuilder.filteredDataLogger(environment.debug))
                .filter(new ActorsSearchFilter(query)) // pass through only items matching the search query
                .filter(new RankedSizerObeyingTagRules(maxItems))
                .filter(debugFiltersBuilder.resultDataLogger(environment.debug))
                .result("all");
    }
}
