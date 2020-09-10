package dfilyustin.translate

import android.app.Application
import dfilyustin.translate.mvi.TranslateActor
import dfilyustin.translate.mvi.TranslateFeature
import dfilyustin.translate.repository.TranslateApi
import dfilyustin.translate.repository.TranslateRepositoryImpl
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class App : Application() {

    lateinit var feature: TranslateFeature

    override fun onCreate() {
        super.onCreate()
        feature = TranslateFeature(
            actor = TranslateActor(
                repository = TranslateRepositoryImpl(
                    Retrofit
                        .Builder()
                        .baseUrl("https://dictionary.skyeng.ru")
                        .addConverterFactory(GsonConverterFactory.create())
                        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                        .build()
                        .create(TranslateApi::class.java)
                ),
                ioScheduler = Schedulers.io(),
                computationScheduler = Schedulers.computation(),
                uiScheduler = AndroidSchedulers.mainThread()
            )
        )
    }
}