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

import com.gurucue.recommendations.blender.DataSet;
import com.gurucue.recommendations.blender.Rank;
import com.gurucue.recommendations.blender.StatefulFilter;
import com.gurucue.recommendations.blender.VideoData;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Filters out content until the dataset size comes down to the preset
 * maximum size, using ranks and tag rules within the provided dataset.
 */
public class RankedSizerObeyingTagRules implements StatefulFilter<VideoData> {
    private final int maxItems;

    public RankedSizerObeyingTagRules(final int maxItems) {
        this.maxItems = maxItems;
    }

    private StringBuilder runLog;

    @Override
    public DataSet<VideoData> transform(final DataSet<VideoData> source) {
        final StringBuilder logBuilder = new StringBuilder(2048);
        runLog = logBuilder;
        final int n = source.size();
        logBuilder.append(getClass().getSimpleName()).append(":\n");

        if (n <= maxItems) {
            logBuilder.append("  * The dataset contains only ").append(n).append(" items, only rank-sorting it without applying any rules\n");
            return source.sort(RankComparator.INSTANCE);
        }

        final DataSet.Builder<VideoData> resultBuilder = new DataSet.Builder<>(source.getDuplicateResolver(), source);

        // determine possible tags and their constraints
        final Map<String, SingleTagData> tagMap = new HashMap<>();
        final Map<String, Integer> limits = source.getLimitTags();
        if ((limits != null) && !limits.isEmpty()) {
            limits.forEach((final String tag, final Integer maxItems) -> tagMap.put(tag, new SingleTagData(tag, maxItems, n)));
        }

        if (tagMap.size() < 2) {
            // if there are not at least 2 tags to choose from, then there is no sense in applying any rules
            logBuilder.append("  * There ");
            if (tagMap.size() == 1) logBuilder.append("is only one tag (").append(tagMap.keySet().iterator().next()).append(")");
            else logBuilder.append("are no tags");
            logBuilder.append(" present, only rank-sorting it and returning first ").append(maxItems).append(" items out of ").append(n).append("\n");
            final Iterator<VideoData> it = source.sort(RankComparator.INSTANCE).iterator();
            int i = maxItems;
            while (it.hasNext() && (i > 0)) {
                resultBuilder.add(it.next());
                i--;
            }
            return resultBuilder.build();
        }

        // split the videos among tags; NOTE: this usually generates duplicates, as content can have more than 1 tag
        final Set<String> danglingTags = new HashSet<>();
        source.forEach((final VideoData videoData) -> {
            for (final String tag : videoData.tags) {
                final SingleTagData data = tagMap.get(tag);
                if (data != null) data.add(videoData);
                else danglingTags.add(tag);
            }
        });
        if (danglingTags.size() > 0) {
            final Iterator<String> it = danglingTags.iterator();
            logBuilder.append("  * Encountered tags not present in PRODUCT_TAGS and/or with a MAX_ITEMS specification: ").append(it.next());
            while (it.hasNext()) logBuilder.append(", ").append(it.next());
            logBuilder.append("\n");
        }

        // construct an array of datas and sort each one by ranks
        final int tagCount = tagMap.size();
        final SingleTagData[] datas = new SingleTagData[tagCount];
        int i = 0;
        for (final SingleTagData entry : tagMap.values()) {
            entry.rankSort();
            datas[i++] = entry;
        }

        // round-robin iteration among datas until we run out of candidates
        final SingleTagData[] workDatas = Arrays.copyOf(datas, datas.length);
        i = 0;
        int tagIndex = 0;
        int remainingTags = workDatas.length;
        while ((i < maxItems) && (remainingTags > 0)) {
            final SingleTagData singleTagData = workDatas[tagIndex];
            if (singleTagData != null) {
                if (singleTagData.isExhausted()) {
                    // cannot choose more content from this tag without violating set MAX_ITEMS_* rule, or there is no more content to be had
                    remainingTags--;
                    workDatas[tagIndex] = null;
                }
                else {
                    final VideoData videoData = singleTagData.next();
                    if (videoData == null) {
                        // cannot choose more content from this tag: no more content
                        // (this shouldn't happen, because it would mean the previous isExhausted() was incorrect)
                        remainingTags--;
                        workDatas[tagIndex] = null;
                    }
                    else {
                        boolean isAcceptable = true;
                        for (final String tag : videoData.tags) {
                            final SingleTagData data = tagMap.get(tag);
                            if (data == null) continue;
                            if (data.wouldViolate()) {
                                // cannot use this content without violating some (other) constraint
                                isAcceptable = false;
                                break;
                            }
                        }
                        if (isAcceptable) {
                            // the content is acceptable, add it
                            resultBuilder.add(videoData);
                            i++;
                            for (final String tag : videoData.tags) {
                                final SingleTagData data = tagMap.get(tag);
                                if (data == null) continue;
                                data.accept(videoData); // mind the duplicates
                            }
                        }
                        else singleTagData.refuse(videoData);
                    }
                }
            }
            tagIndex++;
            if (tagIndex >= tagCount) tagIndex = 0; // another round-robin iteration
        }

        // if we cannot pick maxItems content without violating constraints, then violate constraints in a round-robin fashion
        if (i < maxItems) {
            logBuilder.append("  * Collected ").append(i).append(" out of requested ").append(maxItems).append(" items without violating rules, thereafter the rules had to be violated\n");
            int roundCount = i;
            while (i < maxItems) {
                final SingleTagData singleTagData = datas[tagIndex];
                final VideoData videoData = singleTagData.nextWithRefused();
                if (videoData != null) {
                    resultBuilder.add(videoData);
                    i++;
                    for (final String tag : videoData.tags) {
                        final SingleTagData data = tagMap.get(tag);
                        if (data == null) continue;
                        data.chosenCount++;
                    }
                }
                tagIndex++;
                if (tagIndex >= tagCount) {
                    // another round-robin iteration
                    tagIndex = 0;
                    if (roundCount == i) break; // there was no contribution from any of the tags, continuing from here would be an endless loop
                    roundCount = i;
                }
            }
        }

        logBuilder.append("  * Assembled ").append(i).append(" out of ").append(n).append(" items:\n");
        for (int j = 0; j < tagCount; j++) {
            final SingleTagData singleTagData = datas[j];
            logBuilder.append("      ").append(singleTagData.tag)
                    .append(": available: ").append(singleTagData.count)
                    .append(", limit: ").append(singleTagData.maxItems)
                    .append(", selected: ").append(singleTagData.chosenCount)
                    .append("\n");
        }
        return resultBuilder.build();
    }

    @Override
    public void writeLog(final StringBuilder output) {
        if (runLog == null) return;
        output.append(runLog);
    }

    /**
     * TODO: split this class into a builder and a container.
     */
    public static final class SingleTagData {
        final String tag;
        final int maxItems; // the constraint from tags
        final int upperLimit; // it is guaranteed there will be no more items added than this
        final VideoData[] data;
        final TLongIntMap dataById = new TLongIntHashMap();
        final LinkedList<VideoData> refused = new LinkedList<>();
        int count = 0;
        int refusedCount = 0;
        int currentIndex = 0;
        int chosenCount = 0;

        public SingleTagData(final String tag, final int maxItems, final int upperLimit) {
            this.tag = tag;
            this.maxItems = maxItems;
            this.upperLimit = upperLimit;
            data = new VideoData[upperLimit];
        }

        public void add(final VideoData videoData) {
            data[count++] = videoData;
        }

        // sorts, and builds the mapping (second phase of usage; this is the point where the builder becomes the container)
        public void rankSort() {
            if (count > 1) {
                Arrays.sort(data, 0, count, RankComparator.INSTANCE);
                for (int i = count - 1; i >= 0; i--) {
                    dataById.put(data[i].video.id, i);
                }
            }
            else if (count == 1) {
                dataById.put(data[0].video.id, 0);
            }
        }

        public VideoData next() {
            if (currentIndex >= count) return null;
            VideoData result;
            while ((result = data[currentIndex]) == null) {
                currentIndex++;
                if (currentIndex >= count) return null;
            }
            return result;
        }

        /**
         * Returns true when it cannot return another content without violating
         * the maxItems rule, or when there's no more content to return.
         * @return whether there's no more content to return, or no more content without violating the maxItems rule
         */
        public boolean isExhausted() {
            if (chosenCount >= maxItems) return true;
            if (currentIndex >= count) return true;
            return false;
        }

        public boolean wouldViolate() {
            return chosenCount >= maxItems;
        }

        public void refuse(final VideoData videoData) {
            final int index = dataById.get(videoData.video.id);
            if (index >= 0) {
                data[index] = null;
                refused.add(videoData);
                refusedCount++;
            }
        }

        public void accept(final VideoData videoData) {
            final int index = dataById.get(videoData.video.id);
            if (index >= 0) {
                data[index] = null;
                chosenCount++;
            }
        }

        public VideoData nextWithRefused() {
            final VideoData firstRefused = refused.pollFirst();
            if (firstRefused == null) return next();
            return firstRefused;
        }
    }

    public static final class RankComparator implements Comparator<VideoData> {
        public static final RankComparator INSTANCE = new RankComparator();

        @Override
        public int compare(final VideoData o1, final VideoData o2) {
            if (o1 == null) return 1;
            if (o2 == null) return -1;
            final Rank r1 = o1.rank;
            final Rank r2 = o2.rank;
            if (r1 == null) return 1;
            if (r2 == null) return -1;
            return r2.getRank() - r1.getRank();
        }
    }
}
