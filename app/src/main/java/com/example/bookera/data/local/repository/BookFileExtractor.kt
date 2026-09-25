package com.example.bookera.data.local.repository

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

object BookFileExtractor {

    fun prepareBookFile(
        downloadedFile: File,
        bookId: Long
    ): File {

        if (!downloadedFile.exists()) {
            throw IllegalArgumentException(
                "Downloaded file does not exist: ${downloadedFile.absolutePath}"
            )
        }

        android.util.Log.d(
            "BookFileExtractor",
            "Input file: ${downloadedFile.absolutePath}, size=${downloadedFile.length()}"
        )

        // Проверяем ZIP по сигнатуре
        if (isZip(downloadedFile)) {

            val extractDir = File(
                downloadedFile.parentFile,
                "book_${bookId}_extracted"
            )

            if (!extractDir.exists()) {
                extractDir.mkdirs()
            }

            extractRecursively(
                file = downloadedFile,
                directory = extractDir
            )

            val fb2 = findFb2(extractDir)

            if (fb2 != null) {

                val finalFile = File(
                    downloadedFile.parentFile,
                    "book_$bookId.fb2"
                )

                fb2.copyTo(
                    finalFile,
                    overwrite = true
                )

                android.util.Log.d(
                    "BookFileExtractor",
                    "FB2 found: ${fb2.absolutePath}"
                )

                android.util.Log.d(
                    "BookFileExtractor",
                    "Final FB2: ${finalFile.absolutePath}, size=${finalFile.length()}"
                )

                return finalFile
            }

            throw IllegalStateException(
                "ZIP archive does not contain FB2 file"
            )
        }

        // Если это уже FB2
        if (isFb2(downloadedFile)) {

            val finalFile = File(
                downloadedFile.parentFile,
                "book_$bookId.fb2"
            )

            downloadedFile.copyTo(
                finalFile,
                overwrite = true
            )

            return finalFile
        }

        throw IllegalStateException(
            "Unsupported downloaded file format: ${downloadedFile.name}"
        )
    }

    private fun extractRecursively(
        file: File,
        directory: File
    ) {

        if (isZip(file)) {

            ZipFile(file).use { zip ->
                require(zip.size() <= 2000) { "Archive has too many files" }

                zip.entries().asSequence().forEach { entry ->

                    if (entry.isDirectory) {
                        return@forEach
                    }

                    val outputFile = File(
                        directory,
                        entry.name
                    ).canonicalFile

                    require(outputFile.path.startsWith(directory.canonicalPath + File.separator)) {
                        "Invalid archive entry path"
                    }
                    require(entry.size in 0..100_000_000L) { "Archive entry is too large" }

                    outputFile.parentFile?.mkdirs()

                    zip.getInputStream(entry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    android.util.Log.d(
                        "BookFileExtractor",
                        "Extracted: ${outputFile.absolutePath}"
                    )

                    // Если внутри ещё один ZIP —
                    // распаковываем его тоже
                    if (isZip(outputFile)) {

                        val nestedDir = File(
                            outputFile.parentFile,
                            outputFile.nameWithoutExtension
                        )

                        nestedDir.mkdirs()

                        extractRecursively(
                            outputFile,
                            nestedDir
                        )
                    }
                }
            }
        }
    }

    private fun findFb2(
        directory: File
    ): File? {

        return directory
            .walkTopDown()
            .firstOrNull {
                it.isFile &&
                        it.extension.equals(
                            "fb2",
                            ignoreCase = true
                        )
            }
    }

    private fun isZip(
        file: File
    ): Boolean {

        if (file.length() < 4) {
            return false
        }

        return try {

            FileInputStream(file).use { input ->

                val b1 = input.read()
                val b2 = input.read()
                val b3 = input.read()
                val b4 = input.read()

                b1 == 0x50 &&
                        b2 == 0x4B &&
                        b3 == 0x03 &&
                        b4 == 0x04
            }

        } catch (e: Exception) {
            false
        }
    }

    private fun isFb2(
        file: File
    ): Boolean {

        return try {

            FileInputStream(file).use { input ->

                val buffer = ByteArray(512)

                val count = input.read(buffer)

                if (count <= 0) {
                    return false
                }

                val text = String(
                    buffer,
                    0,
                    count,
                    Charsets.UTF_8
                )

                text.contains("<FictionBook") ||
                        text.contains("<fictionbook")
            }

        } catch (e: Exception) {
            false
        }
    }
}
