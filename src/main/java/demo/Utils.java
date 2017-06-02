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

import com.gurucue.recommendations.blender.BlendParameters;
import com.gurucue.recommendations.entity.product.GeneralVideoProduct;
import com.gurucue.recommendations.entity.value.AttributeValues;

/**
 * Various utility methods for blender processing.
 */
public final class Utils {
    public static final long MAX_CATCHUP_RETENTION = 7L * 24L * 60L * 60L * 1000L; // 7 days in milliseconds

    public static AttributeValues attributes(final BlendParameters parameters) {
        try {
            final AttributeValues attributes = (AttributeValues) parameters.input.get("attributes");
            if (attributes == null) return AttributeValues.NO_VALUES;
            return attributes;
        }
        catch (ClassCastException e) {
            return AttributeValues.NO_VALUES;
        }
    }

    public static int maxItems(final BlendParameters parameters) {
        try {
            final Integer maxItemsObj = (Integer)parameters.input.get("maxItems");
            if (maxItemsObj == null) return 0;
            return maxItemsObj.intValue();
        }
        catch (ClassCastException e) {
            return 0;
        }
    }

    private static final GeneralVideoProduct[] NO_PRODUCTS = new GeneralVideoProduct[0];

    public static GeneralVideoProduct[] referencedProducts(final BlendParameters parameters) {
        try {
            final GeneralVideoProduct[] referencedProducts = (GeneralVideoProduct[])parameters.input.get("referencedProducts");
            if (referencedProducts == null) return NO_PRODUCTS;
            return referencedProducts;
        }
        catch (ClassCastException e) {
            return NO_PRODUCTS;
        }
    }
}
