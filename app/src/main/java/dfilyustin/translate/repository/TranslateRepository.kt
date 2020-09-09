package dfilyustin.translate.repository

import android.util.LruCache
import com.google.gson.annotations.SerializedName
import dfilyustin.translate.model.Translation
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query

interface TranslateRepository {
    fun getTranslation(query: String): Single<List<Translation>>
}

class TranslateRepositoryImpl(private val api: TranslateApi) : TranslateRepository {

    private val cache: LruCache<String, List<Translation>> = LruCache(10)

    override fun getTranslation(query: String): Single<List<Translation>> {
        return api.getTranslation(query).map {
            it.map {
                Translation(
                    it.query,
                    it.meanings.map {
                        Translation.Meaning(
                            it.translation.text,
                            "https:${it.imageUrl}"
                        )
                    })
            }
        }.takeIf { cache.get(query) == null } ?: (Single.just(cache.get(query)))
    }

}

interface TranslateApi {
    @GET("/api/public/v1/words/search")
    fun getTranslation(@Query("search") query: String): Single<List<TranslationResponse>>

    class TranslationResponse(
        @SerializedName("text")
        val query: String,
        val meanings: List<MeaningResponse>
    )

    class MeaningResponse(
        val translation: TranslationValueResponse,
        val imageUrl: String
    )

    class TranslationValueResponse(
        val text: String
    )
}
