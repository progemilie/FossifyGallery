package org.fossify.gallery.models

import androidx.annotation.StringRes
import org.fossify.gallery.R

/**
 * A kind of metadata that can be taken back off a file, as the "remove metadata" dialog offers it.
 *
 * These are the groups a person thinks in, not the blocks a container is made of - the mapping
 * between the two is [org.fossify.gallery.helpers.MetadataStripper]'s business. Declaration order is
 * the order they are listed in, with the one most people are actually after first.
 *
 * [LOCATION] and [ORIENTATION] are the odd ones out: they are fields inside the Exif block rather
 * than blocks of their own, so either can be removed while everything else the camera wrote stays.
 * They part company when [EXIF] goes: the location cannot survive it, but the orientation is written
 * back afterwards, since most people stripping a photo do not mean to have it come out sideways.
 */
enum class MetadataGroup(@param:StringRes val labelRes: Int) {
    LOCATION(R.string.metadata_group_location),
    ORIENTATION(R.string.metadata_group_orientation),
    EXIF(R.string.metadata_group_exif),
    XMP(R.string.metadata_group_xmp),
    IPTC(R.string.metadata_group_iptc),
    ICC(R.string.metadata_group_icc),
    OTHER(R.string.metadata_group_other),
}
