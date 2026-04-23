package io.tl.haptic

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

object HapticTranscoder {
    fun transcode(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onComplete: (Boolean) -> Unit
    ) {
        val tempInput = File(context.cacheDir, "raw_input_temp")
        try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                tempInput.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            onComplete(false)
            return
        }

        val cmd = arrayOf(
            "-y",
            "-i", tempInput.absolutePath,
            "-map_metadata", "-1",
            "-filter_complex", "[0:a]aformat=channel_layouts=stereo,pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1,aresample=async=1",
            "-metadata", "ANDROID_HAPTIC=1",
            "-vn",
            "-ar", "48000",
            "-c:a", "libvorbis",
            "-q:a", "1",
            outputFile.absolutePath
        )

        FFmpegKit.executeWithArgumentsAsync(cmd) { session ->
            val success = ReturnCode.isSuccess(session.returnCode)
            if (tempInput.exists()) tempInput.delete()
            onComplete(success)
        }
    }
}
