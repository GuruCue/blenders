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

import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.blender.BlendEnvironment;
import com.gurucue.recommendations.blender.BlendParameters;
import com.gurucue.recommendations.blender.BlenderResult;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.entity.product.GeneralVideoProduct;
import com.gurucue.recommendations.recommender.RecommendationSettings;
import demo.BasicTagger;
import demo.DebugFiltersBuilder;
import demo.Utils;
import demo.VideoDuplicateResolver;

/**
 * Video blender for similar content.
 */
public final class BlenderSimilar implements RecommendBlender {

    @Override
    public BlenderResult<VideoData> blend(
            final BlendEnvironment environment,
            final BlendParameters parameters,
            int maxItems,
            final String requestedVideoFormat,
            final DebugFiltersBuilder debugFiltersBuilder
    ) throws ResponseException {
        // set default output size, if not provided
        if (maxItems <= 0) maxItems = 20;
        // we need a reference product to determine the series
        final GeneralVideoProduct[] referenceProducts = demo.Utils.referencedProducts(parameters);
        if ((referenceProducts == null) || (referenceProducts.length == 0)) throw new ResponseException(ResponseStatus.RECOMMENDER_NEEDS_PRODUCT, "Cannot list similar content: a video or tv-programme product must be specified to determine similar content");


        // initialize and filter the DataSet
        return VideoData.buildDataSetOfVideosAndTvProgrammes(environment.transaction, environment.partner, environment.consumer, new VideoDuplicateResolver(requestedVideoFormat, environment.requestTimestampMillis), environment.requestTimestampMillis, Utils.MAX_CATCHUP_RETENTION, 0L)
                .filter(new BasicTagger(environment.requestTimestampMillis, maxItems)) // this is an all-pass tagging filter
				.filter(debugFiltersBuilder.allDataLogger(environment.debug))
                .filter(environment.recommenderProvider.similarFilter("demo-all", new long[]{referenceProducts[0].id}, new RecommendationSettings(maxItems, false)))
                .filter(debugFiltersBuilder.filteredDataLogger(environment.debug))
                .filter(debugFiltersBuilder.resultDataLogger(environment.debug))
                .result("similar").feedback("recommender", "demo-all");
    }
}
