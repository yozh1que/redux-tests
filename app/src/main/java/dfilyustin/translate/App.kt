package dfilyustin.translate

import android.app.Application
import android.util.Log
import dfilyustin.translate.mvi.TranslateAction
import dfilyustin.translate.mvi.TranslateActor
import dfilyustin.translate.mvi.TranslateFeature
import dfilyustin.translate.mvi.TranslateState
import dfilyustin.translate.repository.TranslateApi
import dfilyustin.translate.repository.TranslateRepositoryImpl
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.function.Consumer

class App : Application() {
    override fun onCreate() {
        super.onCreate()

//        val api = Retrofit
//            .Builder()
//            .baseUrl("https://dictionary.skyeng.ru")
//            .addConverterFactory(GsonConverterFactory.create())
//            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
//            .build()
//            .create(TranslateApi::class.java)
//        api.getTranslation("dog")
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe { response ->
//                response.map {
//                    Translation(
//                        it.query,
//                        it.meanings.map {
//                            Translation.Meaning(
//                                it.translation.text,
//                                "https:${it.imageUrl}"
//                            )
//                        })
//                }.forEach(::println)
//                val x = ""
//            }

        val feature = TranslateFeature(
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
                uiScheduler = AndroidSchedulers.mainThread()
            )
        )

        feature.subscribe(object : Observer<TranslateState> {
            override fun onComplete() {

                Log.d("DEBUG FEATURE", "onComplete")
            }

            override fun onSubscribe(d: Disposable) {

                Log.d("DEBUG FEATURE", "onSubscribe")
            }

            override fun onNext(t: TranslateState) {
                Log.d("DEBUG FEATURE", t.toString())
            }

            override fun onError(e: Throwable) {
                throw e
            }
        } )

        feature.apply {
            accept(TranslateAction.UpdateQuery("dog"))
            accept(TranslateAction.Submit)
        }

    }
}