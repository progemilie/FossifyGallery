package org.fossify.gallery.models

/**
 * One name/value pair out of a file's metadata.
 *
 * [opensMap] marks the coordinates row in the summary, the one row that does something when tapped.
 */
data class MetadataTag(
    val name: String,
    val value: String,
    val opensMap: Boolean = false,
)

/**
 * A group of tags that were stored together in the file - one metadata-extractor directory, so
 * "JPEG", "Exif IFD0", "GPS", "XMP", "ICC Profile" and so on.
 */
data class MetadataSection(
    val name: String,
    val tags: List<MetadataTag>,
)

/**
 * Everything a file says about itself.
 *
 * [summary] is the handful of fields worth reading first and is shown pinned open; [sections] is
 * every group the file actually carries, each one collapsed until asked for. Nothing here comes
 * from the media database or any cache - it is all read back off the file at the moment it is
 * asked for.
 */
data class FileMetadata(
    val summary: List<MetadataTag>,
    val sections: List<MetadataSection>,
)
