package dfilyustin.translate.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

data class Translation(
    val originalText: String,
    val meanings: List<Meaning>
) {
    @Parcelize
    data class Meaning(
        val translatedText: String,
        val imageUrl: String
    ) : Parcelable
}