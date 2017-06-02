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
import com.gurucue.recommendations.blender.StatelessFilter;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import com.gurucue.recommendations.entity.product.VideoProduct;

import java.util.Set;

/**
 * Basic tagging for all video and tv-programme content.
 */
public final class BasicTagger implements StatelessFilter<VideoData> {
    public static final String FREE = "FREE";
    public static final String PAID = "PAID";
    public static final String CATCHUP = "CATCHUP";
    public static final String LIVETV = "LIVETV";
    public static final String VOD = "VOD";

    public final long recommendTimestampMillis;
    public final int maxItems;

    public BasicTagger(final long recommendTimestampMillis, final int maxItems) {
        this.recommendTimestampMillis = recommendTimestampMillis;
        this.maxItems = maxItems;
    }

    public int countFree = 0;
    public int countPaid = 0;
    public int countCatchup = 0;
    public int countLiveTv = 0;
    public int countVod = 0;
    public int countFreeCatchup = 0;
    public int countPaidCatchup = 0;
    public int countFreeLiveTv = 0;
    public int countPaidLiveTv = 0;
    public int countFreeVod = 0;
    public int countPaidVod = 0;

    @Override
    public boolean test(final VideoData videoData) {
        final Set<String> tags = videoData.tags; // use a local variable for faster access
        if (videoData.isTvProgramme) {
            final boolean isFree;
            if (videoData.isSubscribed) {
                tags.add(FREE);
                countFree++;
                isFree = true;
            }
            else {
                tags.add(PAID);
                countPaid++;
                isFree = false;
            }
            if (((TvProgrammeProduct)videoData.video).endTimeMillis <= recommendTimestampMillis) {
                tags.add(CATCHUP);
                countCatchup++;
                if (isFree) countFreeCatchup++;
                else countPaidCatchup++;
            }
            else {
                tags.add(LIVETV);
                countLiveTv++;
                if (isFree) countFreeLiveTv++;
                else countPaidLiveTv++;
            }
        }
        else {
            tags.add(VOD);
            countVod++;
            if (videoData.isSubscribed) {
                if (((VideoProduct)videoData.video).price > 0.0) {
                    tags.add(PAID);
                    countPaid++;
                    countPaidVod++;
                }
                else {
                    tags.add(FREE);
                    countFree++;
                    countFreeVod++;
                }
            }
            else {
                tags.add(PAID);
                countPaid++;
                countPaidVod++;
            }
        }
        return true;
    }

    private static final String INDENT = "    ";
    StringBuilder maxTagsLog = null;

    @Override
    public void writeLog(final StringBuilder output) {
        final String className = getClass().getSimpleName();
        output.append(className).append(" tagged:\n");
        final int startingLength = output.length();
        if (countFree > 0) output.append(INDENT).append(countFree).append(" ").append(FREE).append("\n");
        if (countPaid > 0) output.append(INDENT).append(countPaid).append(" ").append(PAID).append("\n");
        if (countCatchup > 0) {
            output.append(INDENT).append(countCatchup).append(" ").append(CATCHUP).append("\n");
            output.append(INDENT).append(INDENT).append(countFreeCatchup).append(" ").append(FREE).append(" ").append(CATCHUP).append("\n");
            output.append(INDENT).append(INDENT).append(countPaidCatchup).append(" ").append(PAID).append(" ").append(CATCHUP).append("\n");
        }
        if (countLiveTv > 0) {
            output.append(INDENT).append(countLiveTv).append(" ").append(LIVETV).append("\n");
            output.append(INDENT).append(INDENT).append(countFreeLiveTv).append(" ").append(FREE).append(" ").append(LIVETV).append("\n");
            output.append(INDENT).append(INDENT).append(countPaidLiveTv).append(" ").append(PAID).append(" ").append(LIVETV).append("\n");
        }
        if (countVod > 0) {
            output.append(INDENT).append(countVod).append(" ").append(VOD).append("\n");
            output.append(INDENT).append(INDENT).append(countFreeVod).append(" ").append(FREE).append(" ").append(VOD).append("\n");
            output.append(INDENT).append(INDENT).append(countPaidVod).append(" ").append(PAID).append(" ").append(VOD).append("\n");
        }
        if (startingLength == output.length()) output.append(INDENT).append("none\n"); // nothing was tagged
        if (maxTagsLog != null) output.append(maxTagsLog);
    }

    @Override
    public void onEnd(final DataSet<VideoData> dataSet) {
        final int maxRecommendations = dataSet.size() < maxItems ? dataSet.size() : maxItems;
        final StringBuilder maxTagsLog = new StringBuilder(256);
        this.maxTagsLog = maxTagsLog;
        maxTagsLog.append("  Setting 15:85 PAID:FREE ratio for ").append(maxRecommendations).append(" resulting items\n");
        int maxPaidItems = (maxRecommendations * 15) / 100; // 15% for paid
        if (maxPaidItems > countPaid) {
            maxTagsLog.append("    Requesting ").append(countPaid).append(" PAID items: wanted ").append(maxPaidItems).append(" items, but there are only ").append(countPaid).append(" available\n");
            maxPaidItems = countPaid;
        }
        else maxTagsLog.append("    Requesting ").append(maxPaidItems).append(" PAID items out of ").append(countPaid).append(" available\n");
        int maxFreeItems = maxRecommendations - maxPaidItems;
        if (maxFreeItems > countFree) {
            // increase paid items for the delta between wanted and available free items
            final int delta = maxFreeItems - countFree;
            maxTagsLog.append("    Requesting ").append(countFree).append(" FREE items: wanted ").append(maxFreeItems).append(" items, but there are only ").append(countFree).append(" available\n")
                    .append("      Also increasing requested PAID items to ").append(maxPaidItems+delta).append(" so the total doesn't decrease.\n");
            maxFreeItems = countFree;
            maxPaidItems += delta;
        }
        else maxTagsLog.append("    Requesting ").append(maxFreeItems).append(" FREE items out of ").append(countFree).append(" available\n");

        maxTagsLog.append("  Setting MAX_ITEMS tags:\n");
        if (maxPaidItems > 0) {
            dataSet.setLimit(PAID, maxPaidItems);
            dataSet.setLimit(FREE, maxFreeItems);
            maxTagsLog.append("    MAX_ITEMS_").append(PAID).append("=").append(maxPaidItems)
                    .append("\n    MAX_ITEMS_").append(FREE).append("=").append(maxFreeItems).append("\n");
        }

        if (maxPaidItems <= 0) {
            maxTagsLog.append("    No tags set.\n");
        }
    }
}
