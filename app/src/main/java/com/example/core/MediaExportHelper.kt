package com.example.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object MediaExportHelper {

  fun saveBitmapLocally(context: Context, bitmap: Bitmap, filenamePrefix: String): File {
    val dir = File(context.filesDir, "generated_images").apply { mkdirs() }
    val file = File(dir, "${filenamePrefix}_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file
  }

  fun exportBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Result<String> {
    return runCatching {
      val fileName = "${title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)}_${System.currentTimeMillis()}.png"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
          put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Images.Media.MIME_TYPE, "image/png")
          put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PrinceAIStudio")
          put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
          ?: error("Failed to create MediaStore entry")
        resolver.openOutputStream(uri)?.use { out ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: error("Failed to open image output stream")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Saved to Pictures/PrinceAIStudio/$fileName"
      } else {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "PrinceAIStudio").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "Saved to ${file.absolutePath}"
      }
    }
  }

  fun exportWavToDownloads(context: Context, wavFile: File, title: String): Result<String> {
    return runCatching {
      val fileName = "${title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)}_${System.currentTimeMillis()}.wav"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
          put(MediaStore.Downloads.DISPLAY_NAME, fileName)
          put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
          put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PrinceAIStudio")
          put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
          ?: error("Failed to create download entry")
        resolver.openOutputStream(uri)?.use { out ->
          wavFile.inputStream().use { input -> input.copyTo(out) }
        } ?: error("Failed to open audio output stream")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Saved to Downloads/PrinceAIStudio/$fileName"
      } else {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "PrinceAIStudio").apply { mkdirs() }
        val dest = File(dir, fileName)
        wavFile.copyTo(dest, overwrite = true)
        "Saved to ${dest.absolutePath}"
      }
    }
  }

  fun exportMp4ToMovies(context: Context, mp4File: File, title: String): Result<String> {
    return runCatching {
      val fileName = "${title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)}_${System.currentTimeMillis()}.mp4"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
          put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
          put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/PrinceAIStudio")
          put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
          ?: error("Failed to create Movies MediaStore entry")
        resolver.openOutputStream(uri)?.use { out ->
          mp4File.inputStream().use { input -> input.copyTo(out) }
        } ?: error("Failed to open video output stream")
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Saved to Movies/PrinceAIStudio/$fileName"
      } else {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "PrinceAIStudio").apply { mkdirs() }
        val dest = File(dir, fileName)
        mp4File.copyTo(dest, overwrite = true)
        "Saved to ${dest.absolutePath}"
      }
    }
  }

  fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
  }

  fun shareText(context: Context, title: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, title)
      putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, "Export $title"))
  }
}
