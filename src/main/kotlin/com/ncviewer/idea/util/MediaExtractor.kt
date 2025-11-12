package com.ncviewer.idea.util

import com.intellij.openapi.diagnostic.logger
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory

object MediaExtractor {

    private val logger = logger<MediaExtractor>()
    private val lock = Any()
    private var extractedPath: Path? = null

    fun ensureMediaExtracted(): Path {
        synchronized(lock) {
            extractedPath?.let { return it }

            val tempDir = Files.createTempDirectory("nc-viewer-media")
            logger.info("Extracting media assets to $tempDir")
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
            logger.debug("Copying resources from jar $jarUri")
            copyPath(jarPath, targetDir)
            closeableFs?.close()
        } else {
            val systemPath = Paths.get(uri)
            logger.debug("Copying resources from filesystem $systemPath")
            copyPath(systemPath, targetDir)
        }
    }

    private fun copyPath(sourceRoot: Path, targetDir: Path) {
        logger.debug("Copying asset tree from $sourceRoot to $targetDir")
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
