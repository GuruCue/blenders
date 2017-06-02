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
import com.gurucue.recommendations.blender.BlendEnvironment;
import com.gurucue.recommendations.blender.BlendParameters;
import com.gurucue.recommendations.blender.BlenderResult;
import com.gurucue.recommendations.blender.DataValue;
import com.gurucue.recommendations.blender.TopBlender;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.entity.value.AttributeValues;
import demo.DebugFiltersBuilder;
import demo.Utils;

/**
 * Decision logic for demo recommendation blenders: it decides which one to use and
 * invokes it with appropriate parameters.
 */
public final class MainBlender implements TopBlender {

    public final BlenderAll blenderAll = new BlenderAll();
    public final BlenderMovies blenderMovies = new BlenderMovies();
    public final BlenderTvseries blenderTvseries = new BlenderTvseries();
    public final BlenderEpisodes blenderEpisodes = new BlenderEpisodes();
    public final BlenderSimilar blenderSimilar = new BlenderSimilar();

    @SuppressWarnings("unchecked")
    @Override
    public <V extends DataValue> BlenderResult<V> blend(final Class<V> dataClass, final BlendEnvironment environment, final BlendParameters parameters) throws ResponseException {
        if (dataClass != VideoData.class)
            throw new IllegalArgumentException(MainBlender.class.getCanonicalName() + " operates only on VideoData, not on: " + dataClass.getCanonicalName());

        final RecommendBlender blender;
        if (parameters.blenderName == null) {
            blender = blenderAll;
        }
        else {
            switch (parameters.blenderName) {
                case "all":
                    blender = blenderAll;
                    break;
                case "movies":
                    blender = blenderMovies;
                    break;
                case "tv-series":
                    blender = blenderTvseries;
                    break;
                case "episodes":
                    blender = blenderEpisodes;
                    break;
                case "similar":
                    blender = blenderSimilar;
                    break;
                default:
                    blender = blenderAll;
                    break;
            }
        }

        final AttributeValues attributes = Utils.attributes(parameters);
        // we need the requested video format, if provided
        final String requestedVideoFormat = attributes.getAsString(environment.dataProvider.getAttributeCodes().videoFormat);

        // and the maximum number of results
        final int maxItems = Utils.maxItems(parameters);

        // debugging support: we will create 3 debug logging all-pass filters, for 3 different log outputs (raw dataset, filtered dataset, final result dataset)
        final DebugFiltersBuilder debugFiltersBuilder = new DebugFiltersBuilder(environment.transaction, environment.partner, environment.consumer, environment.requestTimestampMillis);

        return (BlenderResult<V>) blender.blend(environment, parameters, maxItems, requestedVideoFormat, debugFiltersBuilder);
    }
}
