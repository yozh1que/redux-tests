package dfilyustin.translate.model

data class Translation(
    val originalText: String,
    val meanings: List<Meaning>
) {
    data class Meaning(
        val translatedText: String,
        val imageUrl: String
    )
}