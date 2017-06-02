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

import demo.DebugFiltersBuilder;
import demo.EpisodeSorter;
import demo.Utils;
import demo.VideoDuplicateResolver;
import demo.filters.AcceptOnlyEpisodesOfSeries;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.blender.BlendEnvironment;
import com.gurucue.recommendations.blender.BlendParameters;
import com.gurucue.recommendations.blender.BlenderResult;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.entity.product.GeneralVideoProduct;

import java.util.Collections;

/**
 * Video blender for retrieving a list of all available episodes in a series.
 */
public final class BlenderEpisodes implements RecommendBlender {
    @Override
    public BlenderResult<VideoData> blend(
            final BlendEnvironment environment,
            final BlendParameters parameters,
            int maxItems,
            final String requestedVideoFormat,
            final DebugFiltersBuilder debugFiltersBuilder
    ) throws ResponseException {
        // 1) we need a reference product to determine the series
        final GeneralVideoProduct[] referenceProducts = Utils.referencedProducts(parameters);
        if ((referenceProducts == null) || (referenceProducts.length == 0)) throw new ResponseException(ResponseStatus.RECOMMENDER_NEEDS_PRODUCT, "Cannot list episodes: a video or tv-programme product must be specified to determine episodes from the series it belongs to");
        final GeneralVideoProduct referenceProduct = referenceProducts[0];

        // 2) we need the series ID
        final long seriesId = referenceProduct.seriesId;

        // 3) generate the dataset
        if (seriesId > 0L) {
            // the reference product is part of a series: request all content and filter out just the episodes belonging to the series

            // initialize and filter the DataSet
            return VideoData.buildDataSetOfVideosAndTvProgrammes(environment.transaction, environment.partner, environment.consumer, new VideoDuplicateResolver(requestedVideoFormat, environment.requestTimestampMillis), environment.requestTimestampMillis, Utils.MAX_CATCHUP_RETENTION, 0L)
                    .filter(debugFiltersBuilder.allDataLogger(environment.debug))
                    .filter(new AcceptOnlyEpisodesOfSeries(seriesId))
                    .filter(new EpisodeSorter())
                    .filter(debugFiltersBuilder.resultDataLogger(environment.debug))
                    .result("episodes")
                    .feedback("series-id", Long.valueOf(seriesId))
                    .feedback("product-id", Long.valueOf(referenceProduct.id))
                    .feedback("video-id", Long.valueOf(referenceProduct.videoMatchId));
        }

        // the reference product is not part of any series: return just the given product
        return VideoData.buildDataSet(environment.transaction, environment.consumer, new VideoDuplicateResolver(requestedVideoFormat, environment.requestTimestampMillis), environment.requestTimestampMillis, Collections.singletonList(referenceProduct))
                .log("The reference product with product_id=" + referenceProduct.id + ", partner_product_code=" + referenceProduct.partnerProductCode + " is not part of a series\n")
                .result("episodes")
                .feedback("product-id", Long.valueOf(referenceProduct.id))
                .feedback("video-id", Long.valueOf(referenceProduct.videoMatchId));
    }
}
