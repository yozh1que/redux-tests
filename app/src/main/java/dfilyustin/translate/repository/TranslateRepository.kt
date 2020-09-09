package dfilyustin.translate.repository

import dfilyustin.translate.model.Translation
import io.reactivex.Single

interface TranslateRepository {
    fun getTranslation(query: String): Single<Translation>
}

