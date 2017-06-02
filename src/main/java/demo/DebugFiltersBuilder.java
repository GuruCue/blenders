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

import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.blender.NullStatelessFilter;
import com.gurucue.recommendations.blender.StatelessFilter;
import com.gurucue.recommendations.blender.TvChannelData;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.dto.ConsumerEntity;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.entity.product.GeneralVideoProduct;
import com.gurucue.recommendations.entity.product.PackageProduct;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import com.gurucue.recommendations.entity.product.VideoProduct;
import com.gurucue.recommendations.entitymanager.ProductManager;
import gnu.trove.iterator.TLongIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Builder for debug logging filters. They always return true, but store logging
 * output in the process. The logging output is flushed when the log() method
 * is called.
 */
public final class DebugFiltersBuilder {
    private static final Logger log = LogManager.getLogger(DebugFiltersBuilder.class);
    private static final StatelessFilter<VideoData> fakeFilter = NullStatelessFilter.getNullFilter();

    private final Transaction transaction;
    private final Partner partner;
    private final ConsumerEntity consumer;
    private final long requestTimestampMillis;
    private StringBuilder debugPrefix = null;

    public DebugFiltersBuilder(final Transaction transaction, final Partner partner, final ConsumerEntity consumer, final long requestTimestampMillis) {
        this.transaction = transaction;
        this.partner = partner;
        this.consumer = consumer;
        this.requestTimestampMillis = requestTimestampMillis;
    }

    private StringBuilder getDebugPrefix() {
        // lazy init
        if (debugPrefix == null) {
            debugPrefix = new StringBuilder(1024);
            debugPrefix.append("DataSet for the consumer with username ").append(consumer.username)
                    .append(" and ID ").append(consumer.id)
                    .append(" having active subscriptions to ");
            final TLongIterator it = consumer.activeRelationProductIds(requestTimestampMillis).iterator();
            if (it.hasNext()) {
                final ProductManager pm = transaction.getLink().getProductManager();
                Product p = pm.getById(transaction, partner, it.next(), false);
                debugPrefix.append("packages: ").append(p == null ? "(null)" : p.partnerProductCode);
                while (it.hasNext()) debugPrefix.append(", ").append((p = pm.getById(transaction, partner, it.next(), false)) == null ? "(null)" : p.partnerProductCode);
            }
            else debugPrefix.append("no packages");
        }
        return debugPrefix;
    }

    /**
     * Creates and returns a new all-pass debug filter. If <code>doDebug</code>
     * is <code>true</code>, then a stateless filter is returned that logs all
     * data that passes through the filter. The log is buffered untils it is
     * flushed out by calling the {@link StatelessFilter#writeLog(StringBuilder)}
     * method with a <code>null</code> argument. Otherwise if <code>doDebug</code>
     * is <code>false</code>, then a "fake" all-pass filter is returned that
     * does nothing.
     *
     * @param doDebug whether to enable debug output
     * @param loggerName the name of the logger to be used for the debug output
     * @param headingMessage message to be included within the heading information in the log output
     * @return the all-pass debug logging filter
     */
    public StatelessFilter<VideoData> newFilter(final boolean doDebug, final String loggerName, final String headingMessage) {
        if (!doDebug) return fakeFilter;
        return new DebugLoggingFilter(loggerName, headingMessage, getDebugPrefix());
    }

    /**
     * A shorthand for obtaining a new all-pass debug logging filter that will
     * be used to log all data before any filter is applied, it is therefore to
     * be put at the start of the filtering chain. It is configured
     * with standard settings for the logger name and heading message.
     *
     * @param doDebug whether to enable debug output
     * @return the all-pass debug logging filter
     * @see #newFilter(boolean, String, String)
     */
    public StatelessFilter<VideoData> allDataLogger(final boolean doDebug) {
        return newFilter(doDebug, "debug.blender.everything", "starting DataSet");
    }

    /**
     * A shorthand for obtaining a new all-pass debug logging filter that will
     * be used to log all data after all filters have been applied and before
     * a recommender was invoked. It is configured
     * with standard settings for the logger name and heading message.
     *
     * @param doDebug whether to enable debug output
     * @return the all-pass debug logging filter
     * @see #newFilter(boolean, String, String)
     */
    public StatelessFilter<VideoData> filteredDataLogger(final boolean doDebug) {
        return newFilter(doDebug, "debug.blender.filtered", "filtered DataSet");
    }

    /**
     * A shorthand for obtaining a new all-pass debug logging filter that will
     * be used to log all data after all filters have been applied, including
     * a recommendation filter and any result reordering/sorting filters, it is
     * therefore to be put at the end of the filtering chain.
     * It is configured with standard settings for the logger name and heading
     * message.
     *
     * @param doDebug whether to enable debug output
     * @return the all-pass debug logging filter
     * @see #newFilter(boolean, String, String)
     */
    public StatelessFilter<VideoData> resultDataLogger(final boolean doDebug) {
        return newFilter(doDebug, "debug.blender.result", "resulting DataSet");
    }

    /**
     * An all-pass filter with full CSV-style logging. This debug filter is used when debug is enabled.
     */
    public static final class DebugLoggingFilter implements StatelessFilter<VideoData> {
        final Logger logger;
        final String headingMessage;
        final StringBuilder logPrefix;
        final StringBuilder output;
        int rowCount = 0;

        public DebugLoggingFilter(final String loggerName, final String headingMessage, final StringBuilder logPrefix) {
            this.logger = LogManager.getLogger(loggerName);
            this.headingMessage = headingMessage;
            this.logPrefix = logPrefix;
            this.output = new StringBuilder(16384);
        }

        @Override
        public boolean test(final VideoData videoData) {
            rowCount++;
            final boolean isTvProgramme = videoData.isTvProgramme;
            final GeneralVideoProduct video = videoData.video;
            final Set<PackageProduct> packages = new HashSet<>();
            if (isTvProgramme) {
                final TvProgrammeProduct tvProgramme = (TvProgrammeProduct) video;
                output.append(tvProgramme.id);
                output.append(",,");
                final Iterator<TvChannelData> tvInfoIterator1 = videoData.availableTvChannels.iterator();
                if (tvInfoIterator1.hasNext()) {
                    TvChannelData tvData = tvInfoIterator1.next();
                    output.append(tvData.tvChannel.id);
                    packages.addAll(tvData.productPackages);
                    while (tvInfoIterator1.hasNext()) {
                        tvData = tvInfoIterator1.next();
                        output.append(";").append(tvData.tvChannel.id);
                        packages.addAll(tvData.productPackages);
                    }
                }
                output.append(",\"");
                final Iterator<TvChannelData> tvInfoIterator2 = videoData.availableTvChannels.iterator();
                if (tvInfoIterator2.hasNext()) {
                    output.append(tvInfoIterator2.next().tvChannel.partnerProductCode.replace("\"", "\"\""));
                    while (tvInfoIterator2.hasNext()) {
                        output.append(";").append(tvInfoIterator2.next().tvChannel.partnerProductCode.replace("\"", "\"\""));
                    }
                }
                output.append("\",");
                output.append(tvProgramme.videoMatchId);
                output.append(",");
                output.append(tvProgramme.beginTimeMillis / 1000L);
                output.append(",");
                output.append(tvProgramme.endTimeMillis / 1000L);
                output.append(",");
            }
            else {
                packages.addAll(videoData.productPackages);
                output.append(",");
                output.append(video.id);
                output.append(",,,");
                output.append(video.videoMatchId);
                output.append(",,,");
            }

            if (video.title == null) {
                output.append(",");
                log.error("A video without title! product_id=" + video.id);
            }
            else {
                output.append("\"");
                output.append(video.title.asString().replace("\"", "\"\""));
                output.append("\",");
            }
            if (isTvProgramme) {
                output.append(",,");
            }
            else {
                final VideoProduct v = (VideoProduct) video;
                if (v.catalogueId != null) output.append(v.catalogueId);
                output.append(",");
                if (v.price != 0.0) output.append(Double.toString(v.price).replace(",", "."));
                output.append(",");
            }
            output.append(video.isAdult ? "1" : "0");
            output.append(",");
            if (video.parentalRating > 0) output.append(video.parentalRating);
            output.append(",");
            if (video.runTime > 0) output.append(video.runTime);
            output.append(",");
            if (video.seriesId > 0) output.append(video.seriesId);
            output.append(",");
            if (video.videoCategory != null) output.append("\"").append(video.videoCategory.replace("\"", "\"\"")).append("\"");
            output.append(",");
            outputArrayValue(video.genres, output);
            output.append(",");
            if (!isTvProgramme) {
                outputArrayValue(((VideoProduct)video).vodCategories, output);
            }
            output.append(",");
            if (video.imdbLink != null) output.append("\"").append(video.imdbLink.replace("\"", "\"\"")).append("\"");
            output.append(",");
            output.append(videoData.isSubscribed ? "1" : "0");
            output.append(",");
            if (!packages.isEmpty()) {
                output.append("\"");
                final Iterator<PackageProduct> packagesIterator = packages.iterator();
                output.append(packagesIterator.next().partnerProductCode);
                while (packagesIterator.hasNext()) {
                    output.append(";").append(packagesIterator.next().partnerProductCode);
                }
                output.append("\"");
            }
            output.append(",");
            if (!videoData.tags.isEmpty()) {
                output.append("\"");
                final Iterator<String> tagIterator = videoData.tags.iterator();
                output.append(tagIterator.next());
                while (tagIterator.hasNext()) {
                    output.append(";").append(tagIterator.next());
                }
                output.append("\"");
            }
            output.append(",");
            if (video.videoFormat != null) output.append("\"").append(video.videoFormat.replace("\"", "\"\"")).append("\"");
            output.append(",").append(videoData.prediction).append(",");
            if (videoData.explanation != null) output.append("\"").append(videoData.explanation.replace("\"", "\"\"")).append("\"");
            output.append("\n");
            return true;
        }

        @Override
        public void writeLog(final StringBuilder output) {
            // this acts as a flush of the internal StringBuilder, we don't append anything to the provided output
            final StringBuilder logBuilder = new StringBuilder(this.output.length() + logPrefix.length() + 100); // guesstimate
            logBuilder.append("[Thread ").append(Thread.currentThread().getId()).append("] ").append(logPrefix)
                    .append("\nDataSet of size ").append(rowCount);
            if ((headingMessage != null) && (headingMessage.length() > 0)) {
                logBuilder.append(", ").append(headingMessage);
            }
            logBuilder.append("\n\"tv-programme ID\",\"video ID\",\"tv-channel ID\",\"tv-channel partner_product_code\",\"video match ID\",\"begin time\",\"end time\",\"title\",\"catalogue-id\",\"price\",\"is-adult\",\"parental-rating\",\"run-time\",\"series-id\",\"video-category\",\"genres\",\"vod-categories\",\"imdb-url\",\"is-subscribed\",\"package partner_produce_codes\",\"tags\",\"is-HD\",\"prediction\",\"explanation\"\n");
            logBuilder.append(this.output);
            logger.debug(logBuilder.toString());
        }

        private static void outputArrayValue(final String[] values, final StringBuilder output) {
            final int n = values.length;
            if (n == 0) return;
            output.append("\"").append(values[0]);
            for (int i = 1; i < n; i++) {
                output.append(";").append(values[i]);
            }
            output.append("\"");
        }
    }
}
