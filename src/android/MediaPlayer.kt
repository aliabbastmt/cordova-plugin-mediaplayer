package com.tmt.mediaplayer

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONException
import com.tmt.mediaplayer.PlayerService
import com.tmt.mediaplayer.database.objects.Episode
import com.tmt.mediaplayer.extensions.hasMediaItems
import com.tmt.mediaplayer.extensions.play
import com.tmt.mediaplayer.ui.PlayerState
import java.util.Date

class MediaPlayer : CordovaPlugin() {

    protected var mCallbackContext: CallbackContext? = null
    private var mContext: Context? = null

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null // defines the Getter for the MediaController

    private var playerState: PlayerState = PlayerState()
    private val handler: Handler = Handler(Looper.getMainLooper())

    private lateinit var title: String
    private lateinit var audio: String
    private lateinit var cover: String
    private lateinit var isPlaying: String
    private lateinit var streaming: String
    private lateinit var playbackPosition: String
    private lateinit var duration: String

    @RequiresApi(Build.VERSION_CODES.M)
    @Throws(JSONException::class)
    override fun execute(
            action: String,
            data: JSONArray,
            callbackContext: CallbackContext
    ): Boolean {
        mContext = cordova.activity.applicationContext
        mCallbackContext = callbackContext

        var result = true

        try {
            if (action == "play") {
                cordova.threadPool.execute {

                    val mData = data.getJSONObject(0).get("data") as JSONArray

                    title = mData.getJSONObject(0).get("title") as String
                    audio = mData.getJSONObject(0).get("audio") as String
                    cover = mData.getJSONObject(0).get("cover") as String
                    isPlaying = mData.getJSONObject(0).get("isPlaying") as String
                    streaming = mData.getJSONObject(0).get("streaming") as String
                    playbackPosition = mData.getJSONObject(0).get("playbackPosition") as String
                    duration = mData.getJSONObject(0).get("duration") as String

                    playerState = PlayerState(
                            currentEpisodeMediaId = audio,
                            isPlaying = isPlaying.toBoolean(),
                            playbackSpeed = 1.0f,
                            sleepTimerRunning = false,
                            streaming = streaming.toBoolean(),
                            upNextEpisodeMediaId = ""
                    )

                    initializePlayer()

                    result = true
                }
            } else {
                handleError("Invalid action")
                result = false
            }
        } catch (e: Exception) {
            handleException(e)
            result = false
        }

        return result
    }

    /* Initializes the MediaController - handles connection to PlayerService under the hood */
    private fun initializePlayer() {
        controllerFuture = MediaController.Builder(
                mContext!!,
                SessionToken(mContext!!, ComponentName(mContext!!, PlayerService::class.java))
        ).buildAsync()
        controllerFuture.addListener({ setupController() }, MoreExecutors.directExecutor())
    }

    /* Sets up the MediaController  */
    private fun setupController() {
        val controller: MediaController = this.controller ?: return

        // update playback progress state
        togglePeriodicProgressUpdateRequest()

        controller.addListener(playerListener)

        val selectedEpisode = Episode(
                mediaId = audio,
                guid = "",
                title = title,
                audio = audio,
                cover = cover,
                smallCover = cover,
                publicationDate = Date(),
                isPlaying = isPlaying.toBoolean(),
                playbackPosition = 0L,
                duration = 0L,
                manuallyDeleted = false,
                manuallyDownloaded = false,
                podcastName = "",
                remoteAudioFileLocation = "",
                remoteAudioFileName = "",
                remoteCoverFileLocation = "",
                episodeRemotePodcastFeedLocation = ""
        )

        playerState.currentEpisodeMediaId = selectedEpisode.mediaId
        // start playback
        CoroutineScope(Dispatchers.IO).launch {
            val position = 0L // reset position, if episode is finished
            withContext(Dispatchers.Main) {
                controller.play(
                        Episode(selectedEpisode, playbackPosition = position),
                        playerState.streaming
                )
            }
        }
    }

    /*
    * Runnable: Periodically requests playback position (and sleep timer if running)
    */
    private val periodicProgressUpdateRequestRunnable: Runnable = object : Runnable {
        override fun run() {

            // update progress bar - only if controller is prepared with a media item
            if (controller?.hasMediaItems() == true) {
                Log.e("Player position", "" + controller!!.currentPosition + " : " + controller!!.currentPosition)

            }
            // use the handler to start runnable again after specified delay
            handler.postDelayed(this, 500)
        }
    }

    private fun togglePeriodicProgressUpdateRequest() {
        when (controller?.isPlaying) {
            true -> {
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
                handler.postDelayed(periodicProgressUpdateRequestRunnable, 0)
            }

            else -> {
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
            }
        }
    }

    /*
     * Player.Listener: Called when one or more player states changed.
     */
    private var playerListener: Player.Listener = object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            // store new episode
            playerState.currentEpisodeMediaId = mediaItem?.mediaId ?: String()
            // update episode specific views4
//            updatePlayerViews()
            // clear up next, if necessary
            if (playerState.upNextEpisodeMediaId == mediaItem?.mediaId) {
//                updateUpNext(String())
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // store state of playback
            playerState.isPlaying = isPlaying

            // turn on/off periodic playback position updates
            togglePeriodicProgressUpdateRequest()

            if (isPlaying) {
                // playback is active
            } else {
                // playback is not active

                playerState.sleepTimerRunning = false
                // Not playing because playback is paused, ended, suppressed, or the player
                // is buffering, stopped or failed. Check player.getPlayWhenReady,
                // player.getPlaybackState, player.getPlaybackSuppressionReason and
                // player.getPlaybackError for details.
                when (controller?.playbackState) {
                // player is able to immediately play from its current position
                    Player.STATE_READY -> {

                    }
                // buffering - data needs to be loaded
                    Player.STATE_BUFFERING -> {

                    }
                // player finished playing all media
                    Player.STATE_ENDED -> {

                    }
                // initial state or player is stopped or playback failed
                    Player.STATE_IDLE -> {

                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            if (!playWhenReady) {
                if (controller?.mediaItemCount == 0) {
                    // stopSelf()
                }
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                        // playback reached end: stop / end playback
                    }

                    else -> {
                        // playback has been paused by user or OS: update media session and save state
                        // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY or
                        // PLAY_WHEN_READY_CHANGE_REASON_REMOTE
                        // handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
                    }
                }
            }

        }


    }

    private fun handleError(errorMsg: String) {
        try {
            Log.e(TAG, errorMsg)
            mCallbackContext!!.error(errorMsg)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    private fun handleException(exception: Exception) {
        handleError(exception.toString())
    }

    companion object {
        protected const val TAG = "MediaPlayer"
    }
}
