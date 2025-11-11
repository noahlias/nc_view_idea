package com.ncviewer.idea.util

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory

object MediaExtractor {

    private val lock = Any()
    private var extractedPath: Path? = null

    fun ensureMediaExtracted(): Path {
        synchronized(lock) {
            extractedPath?.let { return it }

            val tempDir = Files.createTempDirectory("nc-viewer-media")
            copyResourceDirectory("/ncviewer/media", tempDir)
            extractedPath = tempDir
            return tempDir
        }
    }

    private fun copyResourceDirectory(resourcePath: String, targetDir: Path) {
        val resource = MediaExtractor::class.java.getResource(resourcePath)
            ?: throw IllegalStateException("Missing bundled media at $resourcePath")
        val uri = resource.toURI()

        if (uri.scheme == "jar") {
            val raw = uri.toString()
            val separator = raw.indexOf("!")
            val jarUri = java.net.URI.create(raw.substring(0, separator))
            val entryPath = raw.substring(separator + 1)

            var closeableFs: java.nio.file.FileSystem? = null
            val fs = try {
                FileSystems.getFileSystem(jarUri)
            } catch (_: Exception) {
                FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).also { closeableFs = it }
            }

            val jarPath = fs.getPath(entryPath)
            copyPath(jarPath, targetDir)
            closeableFs?.close()
        } else {
            val systemPath = Paths.get(uri)
            copyPath(systemPath, targetDir)
        }
    }

    private fun copyPath(sourceRoot: Path, targetDir: Path) {
        Files.walk(sourceRoot).use { paths ->
            paths.forEach { source ->
                val relative = sourceRoot.relativize(source).toString()
                val destination = targetDir.resolve(relative)
                if (source.isDirectory()) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(source, destination)
                }
            }
        }
    }
}
