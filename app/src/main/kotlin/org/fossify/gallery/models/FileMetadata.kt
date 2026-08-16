package org.fossify.gallery.models

/**
 * One name/value pair out of a file's metadata.
 *
 * Two rows of the summary do something when tapped: [opensMap] marks the coordinates, [editable]
 * the description, which is the one field of a file this app writes from here.
 */
data class MetadataTag(
    val name: String,
    val value: String,
    val opensMap: Boolean = false,
    val editable: Boolean = false,
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
 *
 * [removable] is what of it this app is able to take back off the file, which is a shorter list:
 * empty for a format that cannot be rewritten at all.
 */
data class FileMetadata(
    val summary: List<MetadataTag>,
    val sections: List<MetadataSection>,
    val removable: List<MetadataGroup>,
)
