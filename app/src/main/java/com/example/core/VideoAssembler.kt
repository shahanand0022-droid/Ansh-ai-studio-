package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class VideoRenderSceneInput(
  val sceneNumber: Int,
  val durationSeconds: Int,
  val captionText: String,
  val cameraDirection: String,
  val bitmap: Bitmap?,
)

enum class VideoResolutionPreset(val label: String, val shortSide: Int) {
  HD_720("720p HD", 720),
  FHD_1080("1080p Full HD", 1080),
}

object VideoAssembler {

  private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
  private const val FRAME_RATE = 10
  private const val I_FRAME_INTERVAL = 2

  fun resolveDimensions(aspectLabel: String, resolution: VideoResolutionPreset): Pair<Int, Int> {
    val s = resolution.shortSide
    return when {
      aspectLabel.contains("9:16") -> s to ((s * 16) / 9).even()
      aspectLabel.contains("1:1") -> s to s
      aspectLabel.contains("4:3") -> ((s * 4) / 3).even() to s
      else -> ((s * 16) / 9).even() to s
    }
  }

  private fun Int.even(): Int = if (this % 2 == 0) this else this + 1

  suspend fun assembleMp4(
    context: Context,
    title: String,
    scenes: List<VideoRenderSceneInput>,
    aspectLabel: String,
    resolution: VideoResolutionPreset,
    onProgress: (Float, String) -> Unit,
  ): Result<File> = withContext(Dispatchers.Default) {
    runCatching {
      require(scenes.isNotEmpty()) { "At least one scene is required to assemble an MP4 video." }
      val (width, height) = resolveDimensions(aspectLabel, resolution)
      val outDir = File(context.filesDir, "generated_videos").apply { mkdirs() }
      val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "prince_video" }
      val outFile = File(outDir, "${safeName}_${System.currentTimeMillis()}.mp4")

      val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, if (resolution == VideoResolutionPreset.FHD_1080) 3_600_000 else 1_800_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
      }

      val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
      var muxer: MediaMuxer? = null
      var inputSurface: android.view.Surface? = null
      var muxerStarted = false
      var trackIndex = -1

      try {
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = encoder.createInputSurface()
        encoder.start()
        muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val bufferInfo = MediaCodec.BufferInfo()
        val fixedInfo = MediaCodec.BufferInfo()
        val totalFramesAll = scenes.sumOf { (it.durationSeconds.coerceAtLeast(2) * FRAME_RATE) }.coerceAtLeast(1)
        var renderedFrames = 0
        var outputSampleIndex = 0L
        val frameDurationUs = 1_000_000L / FRAME_RATE

        fun drainEncoder(endOfStream: Boolean) {
          if (endOfStream) {
            encoder.signalEndOfInputStream()
          }
          while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
              if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
              check(!muxerStarted) { "MediaCodec format changed twice" }
              trackIndex = muxer.addTrack(encoder.outputFormat)
              muxer.start()
              muxerStarted = true
            } else if (status >= 0) {
              val encodedData = encoder.getOutputBuffer(status)
              if (encodedData != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                if (muxerStarted) {
                  encodedData.position(bufferInfo.offset)
                  encodedData.limit(bufferInfo.offset + bufferInfo.size)
                  fixedInfo.set(
                    bufferInfo.offset,
                    bufferInfo.size,
                    outputSampleIndex * frameDurationUs,
                    bufferInfo.flags,
                  )
                  muxer.writeSampleData(trackIndex, encodedData, fixedInfo)
                  outputSampleIndex++
                }
              }
              encoder.releaseOutputBuffer(status, false)
              if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                break
              }
            }
          }
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.argb(185, 13, 18, 33)
        }
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.rgb(56, 189, 248)
          textSize = (width * 0.028f).coerceAtLeast(22f)
          isFakeBoldText = true
        }
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          textSize = (width * 0.032f).coerceAtLeast(24f)
        }

        scenes.forEach { scene ->
          val sceneFrames = (scene.durationSeconds.coerceAtLeast(2) * FRAME_RATE)
          for (frameIdx in 0 until sceneFrames) {
            coroutineContext.ensureActive()
            val t = frameIdx.toFloat() / sceneFrames.toFloat().coerceAtLeast(1f)
            val canvas: Canvas = inputSurface.lockCanvas(null)
            try {
              drawSceneFrame(
                canvas = canvas,
                width = width,
                height = height,
                scene = scene,
                progress = t,
                bgPaint = bgPaint,
                scrimPaint = scrimPaint,
                badgePaint = badgePaint,
                badgeTextPaint = badgeTextPaint,
                captionPaint = captionPaint,
              )
            } finally {
              inputSurface.unlockCanvasAndPost(canvas)
            }
            renderedFrames++
            if (renderedFrames % FRAME_RATE == 0 || renderedFrames == totalFramesAll) {
              val pct = renderedFrames.toFloat() / totalFramesAll.toFloat()
              onProgress(pct, "Encoding Scene ${scene.sceneNumber} (${(pct * 100).toInt()}%)")
            }
            drainEncoder(false)
          }
        }

        drainEncoder(true)
        onProgress(1f, "Finalizing MP4 video...")
      } finally {
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        runCatching { inputSurface?.release() }
        if (muxerStarted) {
          runCatching { muxer?.stop() }
        }
        runCatching { muxer?.release() }
      }

      check(outFile.exists() && outFile.length() > 0L) {
        "MP4 output file could not be generated by device MediaCodec."
      }
      outFile
    }
  }

  private fun drawSceneFrame(
    canvas: Canvas,
    width: Int,
    height: Int,
    scene: VideoRenderSceneInput,
    progress: Float,
    bgPaint: Paint,
    scrimPaint: Paint,
    badgePaint: Paint,
    badgeTextPaint: TextPaint,
    captionPaint: TextPaint,
  ) {
    canvas.drawColor(Color.rgb(7, 9, 18))

    val bmp = scene.bitmap
    if (bmp != null && !bmp.isRecycled) {
      val zoom = 1.0f + (0.08f * progress)
      val scale = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height) * zoom
      val drawW = bmp.width * scale
      val drawH = bmp.height * scale
      val panX = ((width - drawW) * 0.5f) + ((progress - 0.5f) * (width * 0.03f))
      val panY = (height - drawH) * 0.5f
      val srcRect = Rect(0, 0, bmp.width, bmp.height)
      val dstRect = RectF(panX, panY, panX + drawW, panY + drawH)
      canvas.drawBitmap(bmp, srcRect, dstRect, bgPaint)
    } else {
      val grad = LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        intArrayOf(Color.rgb(13, 18, 33), Color.rgb(20, 42, 74), Color.rgb(32, 22, 68)),
        null,
        Shader.TileMode.CLAMP,
      )
      bgPaint.shader = grad
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
      bgPaint.shader = null
    }

    // Bottom readability gradient scrim
    scrimPaint.shader = LinearGradient(
      0f,
      height * 0.52f,
      0f,
      height.toFloat(),
      Color.TRANSPARENT,
      Color.argb(225, 7, 9, 18),
      Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, height * 0.52f, width.toFloat(), height.toFloat(), scrimPaint)
    scrimPaint.shader = null

    // Top-left Scene pill
    val badgeText = "SCENE ${scene.sceneNumber} • ${scene.cameraDirection.take(36)}"
    val padX = width * 0.03f
    val padY = height * 0.04f
    val badgeW = (badgeTextPaint.measureText(badgeText) + 32f).coerceAtMost(width * 0.85f)
    val badgeH = badgeTextPaint.textSize * 1.9f
    canvas.drawRoundRect(
      RectF(padX, padY, padX + badgeW, padY + badgeH),
      18f,
      18f,
      badgePaint,
    )
    canvas.drawText(badgeText, padX + 16f, padY + badgeH * 0.68f, badgeTextPaint)

    // Bottom caption narration
    val caption = scene.captionText.trim()
    if (caption.isNotBlank()) {
      val maxTextWidth = (width * 0.88f).toInt().coerceAtLeast(100)
      val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, captionPaint, maxTextWidth)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(3)
        .build()
      canvas.save()
      val yTop = (height - layout.height - (height * 0.07f)).coerceAtLeast(height * 0.55f)
      canvas.translate((width - maxTextWidth) * 0.5f, yTop)
      layout.draw(canvas)
      canvas.restore()
    }
  }
}
