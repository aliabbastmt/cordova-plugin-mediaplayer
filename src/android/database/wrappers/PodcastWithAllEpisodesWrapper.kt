/*
 * PodcastWithAllEpisodesWrapper.kt
 * Implements the PodcastWithAllEpisodesWrapper class
 * A PodcastWithAllEpisodesWrapper object holds the base data of a podcast and a list of all its episodes - a wrapper for Podcast & Episode(s)
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.tmt.mediaplayer.database.wrappers

import androidx.room.Embedded
import androidx.room.Relation
import com.tmt.mediaplayer.database.objects.Episode
import com.tmt.mediaplayer.database.objects.Podcast
import com.tmt.mediaplayer.xml.RssHelper


/*
 * PodcastWithAllEpisodesWrapper class
 */
data class PodcastWithAllEpisodesWrapper(
    @Embedded
        val data: Podcast,

    @Relation(parentColumn = "remote_podcast_feed_location", entityColumn = "episode_remote_podcast_feed_location")
        val episodes: List<Episode>
) {

        /* Constructor that uses output from RssHelper */
        constructor(rssPodcast: RssHelper.RssPodcast) : this (
                data = Podcast(rssPodcast),
                episodes = rssPodcast.episodes.map { Episode(it) }
        )

}