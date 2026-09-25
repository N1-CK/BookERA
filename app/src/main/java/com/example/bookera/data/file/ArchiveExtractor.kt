package com.example.bookera.data.file

import java.io.File
import java.util.zip.ZipInputStream

object ArchiveExtractor {

    fun extractZip(
        zipFile: File,
        destinationDir: File
    ): File {

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        var extractedFile: File? = null

        ZipInputStream(
            zipFile.inputStream().buffered()
        ).use { zip ->

            var entry = zip.nextEntry

            while (entry != null) {

                if (!entry.isDirectory) {

                    val outputFile =
                        File(
                            destinationDir,
                            File(entry.name).name
                        )

                    outputFile.outputStream().buffered().use { output ->
                        zip.copyTo(output)
                    }

                    if (
                        outputFile.extension.equals(
                            "fb2",
                            ignoreCase = true
                        )
                    ) {
                        extractedFile = outputFile
                    }
                }

                zip.closeEntry()

                entry = zip.nextEntry
            }
        }

        return extractedFile
            ?: throw IllegalArgumentException(
                "FB2 file not found inside archive"
            )
    }
}