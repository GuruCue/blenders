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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.gurucue.recommendations.blender.Rank;
import com.gurucue.recommendations.blender.StatelessFilter;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.entity.Attribute;
import com.gurucue.recommendations.entity.value.TranslatableValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

/**
 * The actors search filter: passes through only content with a substring
 * match of the given search string in fields: title, title2, director,
 * actor, or an exact match of the production year. The search string is
 * first .
 */
public final class ActorsSearchFilter implements StatelessFilter<VideoData> {
    private final Matcher matcher;
    private final String query;
    private final String[] words;
    private int allCount = 0;
    private int matchedCount = 0;

    public ActorsSearchFilter(final String searchQuery) {
        this.query = searchQuery;
        final String[] words = searchQuery.toLowerCase().split(" ");
        // collapse the array of words by excluding invalid (=empty) query words
        final int wordCount = words.length;
        int dstPos = 0;
        int srcPos = 0;
        while (srcPos < wordCount) {
            final String word = words[srcPos];
            if ((word == null) || (word.length() == 0)) break;
            dstPos++;
            srcPos++;
        }
        srcPos++;
        while (srcPos < wordCount) {
            final String word = words[srcPos];
            if ((word != null) && (word.length() > 0)) {
                words[dstPos++] = word;
            }
            srcPos++;
        }
        final String[] finalWords = (dstPos + 1) < wordCount ? Arrays.copyOf(words, dstPos) : words;
        this.words = finalWords;
        // create the matcher
        final FieldSearch[] wordMatchers = new FieldSearch[1];
        wordMatchers[0] = new TranslationsFieldSearch(finalWords, videoData -> videoData.video.actors);
        matcher = new Matcher(wordMatchers, finalWords);
    }

    @Override
    public boolean test(final VideoData videoData) {
        allCount++;
        final MatchMatrix matrix = matcher.match(videoData);
        if (!matrix.allWordsMatched()) return false; // not all words match
        matchedCount++;
        videoData.rank = new SearchRank(matrix); // assign it a rank, so it can be sorted
        return true;
    }

    @Override
    public void writeLog(final StringBuilder output) {
        output.append(getClass().getSimpleName()).append(": query=\"").append(query).append("\", words=[");
        final int n = words.length;
        if (n > 0) {
            output.append("\"").append(words[0]).append("\"");
            for (int i = 1; i < n; i++) output.append(", \"").append(words[i]).append("\"");
        }
        output.append("], matched ").append(matchedCount).append(" out of ").append(allCount).append(" items\n");
    }

    public interface FieldSearch {
        MatchData[] search(VideoData videoData);
    }

    public static final class StringFieldSearch implements FieldSearch {
        private final String[] words;
        private final Function<VideoData, String> getter;
        private final int wordCount;
        private final MatchData[] noMatches;

        public StringFieldSearch(final String[] words, final Function<VideoData, String> getter) {
            this.words = words;
            this.getter = getter;
            this.wordCount = words.length;
            noMatches = new MatchData[wordCount];
            for (int i = wordCount - 1; i >= 0; i--) noMatches[i] = MatchData.NO_MATCH;
        }

        public MatchData[] search(final VideoData videoData) {
            final String val = getter.apply(videoData);
            if ((val == null) || (val.length() == 0)) return noMatches;
            final String value = val.toLowerCase();
            final int n = value.length();
            final MatchData[] result = new MatchData[wordCount];
            for (int i = wordCount - 1; i >= 0; i--) {
                final String word = words[i];
                final int wordLength = word.length();
                final MatchDetail[] matches = new MatchDetail[n]; // max. possible number of matches: every index matches
                int matchCount = 0;
                for (int pos = value.indexOf(word, 0); pos >= 0; pos = value.indexOf(word, pos + wordLength)) {
                    matches[matchCount++] = new MatchDetail(pos, wordLength);
                }
                result[i] = new MatchData(matches, matchCount);
            }
            return result;
        }
    }

    public static final class TranslationFieldSearch implements FieldSearch {
        private final String[] words;
        private final Function<VideoData, TranslatableValue> getter;
        private final int wordCount;
        private final MatchData[] noMatches;

        public TranslationFieldSearch(final String[] words, final Function<VideoData, TranslatableValue> getter) {
            this.words = words;
            this.getter = getter;
            this.wordCount = words.length;
            noMatches = new MatchData[wordCount];
            for (int i = wordCount - 1; i >= 0; i--) noMatches[i] = MatchData.NO_MATCH;
        }

        public MatchData[] search(final VideoData videoData) {
            final TranslatableValue translatableValue = getter.apply(videoData);
            if ((translatableValue == null) || (translatableValue.translations == null)) return noMatches;
            final ImmutableCollection<String> translations = translatableValue.translations.values();
            if (translations.size() == 0) return noMatches;
            final MatchData[] result = new MatchData[wordCount];
            for (int i = wordCount - 1; i >= 0; i--) {
                final String word = words[i];
                final int wordLength = word.length();
                final ArrayList<MatchDetail> matches = new ArrayList<>(32); // we hope there won't ever be more than 32 matches, else it will grow anyway but with a performance penalty
                for (final String val : translations) {
                    if (val == null) continue;
                    final String value = val.toLowerCase();
                    for (int pos = value.indexOf(word, 0); pos >= 0; pos = value.indexOf(word, pos + wordLength)) {
                        matches.add(new MatchDetail(pos, wordLength));
                    }
                }
                final int n = matches.size();
                result[i] = new MatchData(matches.toArray(new MatchDetail[n]), n);
            }
            return result;
        }
    }

    public static final class TranslationsFieldSearch implements FieldSearch {
        private final String[] words;
        private final Function<VideoData, TranslatableValue[]> getter;
        private final int wordCount;
        private final MatchData[] noMatches;

        public TranslationsFieldSearch(final String[] words, final Function<VideoData, TranslatableValue[]> getter) {
            this.words = words;
            this.getter = getter;
            this.wordCount = words.length;
            noMatches = new MatchData[wordCount];
            for (int i = wordCount - 1; i >= 0; i--) noMatches[i] = MatchData.NO_MATCH;
        }

        public MatchData[] search(final VideoData videoData) {
            final TranslatableValue[] translatableValues = getter.apply(videoData);
            if ((translatableValues == null) || (translatableValues.length == 0)) return noMatches;
            final int arraySize = translatableValues.length;
            final MatchData[] result = new MatchData[wordCount];
            for (int i = wordCount - 1; i >= 0; i--) {
                final String word = words[i];
                final int wordLength = word.length();
                final ArrayList<MatchDetail> positions = new ArrayList<>(32); // we hope there won't ever be more than 32 matches, else it will grow anyway but with a performance penalty
                for (int j = 0; j < arraySize; j++) {
                    final TranslatableValue translatableValue = translatableValues[j];
                    if ((translatableValue == null) || (translatableValue.translations == null)) continue;
                    final ImmutableCollection<String> translations = translatableValue.translations.values();
                    if (translations.size() == 0) continue;
                    for (final String val : translations) {
                        if (val == null) continue;
                        final String value = val.toLowerCase();
                        for (int pos = value.indexOf(word, 0); pos >= 0; pos = value.indexOf(word, pos + wordLength)) {
                            positions.add(new MatchDetail(pos, wordLength));
                        }
                    }
                }
                final int n = positions.size();
                result[i] = new MatchData(positions.toArray(new MatchDetail[n]), n);
            }
            return result;
        }
    }

    public static final class ProductionYearFieldSearch implements FieldSearch {
        private static final MatchData MATCH = new MatchData(new MatchDetail[]{new MatchDetail(0, 4)}, 1);
        private final int wordCount;
        private final int[] matchValues;
        private final int[] skipIndexes;
        private final MatchData[] noMatches;

        public ProductionYearFieldSearch(final String[] words) {
            wordCount = words.length;
            final int[] skipIndexes = new int[wordCount]; // max possible length
            final int[] matchValues = new int[wordCount];
            int skipIndexCount = 0;
            int matchValueCount = 0;
            for (int i = 0; i < wordCount; i++) {
                try {
                    final int value = Integer.parseInt(words[i], 10);
                    matchValues[matchValueCount++] = value;
                }
                catch (NumberFormatException e) {
                    skipIndexes[skipIndexCount++] = i;
                }
            }
            this.matchValues = Arrays.copyOf(matchValues, matchValueCount);
            this.skipIndexes = Arrays.copyOf(skipIndexes, skipIndexCount);
            noMatches = new MatchData[wordCount];
            for (int i = wordCount - 1; i >= 0; i--) noMatches[i] = MatchData.NO_MATCH;
        }

        @Override
        public MatchData[] search(final VideoData videoData) {
            if (matchValues.length == 0) return noMatches; // no integer keywords
            final int productionYear = videoData.video.productionYear;
            if (productionYear <= 0) return noMatches; // production year not available
            final MatchData[] result = new MatchData[wordCount];
            int matchPos = 0;
            int skipPos = 0;
            for (int index = 0; index < wordCount; index++) {
                if ((skipPos < skipIndexes.length) && (skipIndexes[skipPos] == index)) {
                    result[index] = MatchData.NO_MATCH;
                    skipPos++;
                }
                else if (matchPos < matchValues.length) {
                    if (matchValues[matchPos] == productionYear) result[index] = MATCH;
                    else result[index] = MatchData.NO_MATCH;
                    matchPos++;
                }
                else {
                    result[index] = MatchData.NO_MATCH;
                }
            }
            return result;
        }
    }

    public static final class MatchData {
        private static final MatchData NO_MATCH = new MatchData(new MatchDetail[0], 0);
        public final int matchCount;
        public final MatchDetail[] matches;

        public MatchData(final MatchDetail[] matches, int matchCount) {
            if (matchCount > matches.length) matchCount = matches.length;
            this.matchCount = matchCount;
            this.matches = matches;
        }
    }

    public static final class MatchDetail {
        public final int position;
        public final int length;

        public MatchDetail(final int position, final int length) {
            this.position = position;
            this.length = length;
        }
    }

    public static final class Matcher {
        private final FieldSearch[] wordMatchers;
        private final int matcherCount;
        private final String[] words;
        private final int wordCount;

        public Matcher(final FieldSearch[] wordMatchers, final String[] words) {
            this.wordMatchers = wordMatchers;
            this.matcherCount = wordMatchers.length;
            this.words = words;
            this.wordCount = words.length;
        }

        public MatchMatrix match(final VideoData videoData) {
            final MatchData[][] data = new MatchData[matcherCount][];
            for (int i = 0; i < matcherCount; i++) data[i] = wordMatchers[i].search(videoData);
            return new MatchMatrix(matcherCount, wordCount, data);
        }
    }

    public static final class MatchMatrix {
        public final int fieldCount; // matrix rows
        public final int wordCount; // matrix columns
        public final MatchData[][] matches;

        public MatchMatrix(final int fieldCount, final int wordCount, final MatchData[][] matchesByWords) {
            this.fieldCount = fieldCount;
            this.wordCount = wordCount;
            this.matches = matchesByWords;
        }

        public boolean allWordsMatched() {
            nextWord:
            for (int wordIndex = 0; wordIndex < wordCount; wordIndex++) {
                // find out if any field matched the word
                for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                    if (matches[fieldIndex][wordIndex].matchCount > 0) continue nextWord;
                }
                // if we got to here then no field matches the current word
                return false;
            }
            return true;
        }
    }

    /**
     * Ranks the match matrix according to criteria:
     * - match closer to the start of the field has greater weight,
     * - multiple different matches on the same field match stronger the closer they are. // TODO
     */
    public static final class SearchRank implements Rank {
        private static final int[] matchIndexWeights = {100, 75, 60, 50, 42, 35, 28, 22, 18, 14, 11, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        private static final int[] matchDistanceWeights = {15, 10, 7, 5, 4, 3, 2}; // distance 0 would mean the two words are concatenated, this should rarely occur
        private final int rank;

        public SearchRank(final MatchMatrix matrix) {
            final int fieldCount = matrix.fieldCount;
            final int wordCount = matrix.wordCount;
            final MatchData[][] matches = matrix.matches;
            int fieldRanks[] = new int[fieldCount]; // initialized with zeroes by JVM
            // initial rank: whether a field matched or not
            nextField:
            for (int i = 0; i < fieldCount; i++) {
                final MatchData[] fieldMatches = matches[i];
                for (int j = 0; j < wordCount; j++) {
                    final MatchData matchCell = fieldMatches[j];
                    if (matchCell.matchCount > 0) {
                        // closer to the start of field matches stronger
                        final int pos = matchCell.matches[0].position;
                        if (pos >= matchIndexWeights.length) fieldRanks[i] = 1; // default
                        else fieldRanks[i] = matchIndexWeights[pos]; // weighted match
                        continue nextField; // TODO: more words matching on the same field should weigh more
                    }
                }
                fieldRanks[i] = 0;
            }
            // sum the ranks
            int rank = fieldRanks[0];
            for (int i = 1; i < fieldCount; i++) rank += fieldRanks[i];
            this.rank = rank;
        }

        @Override
        public int getRank() {
            return rank;
        }

        @Override
        public ImmutableSet<Attribute> getMatchedAttributes() {
            return null;
        }
    }
}
