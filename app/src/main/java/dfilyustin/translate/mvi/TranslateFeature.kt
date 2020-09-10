package dfilyustin.translate.mvi

import android.util.Log
import com.badoo.mvicore.element.Actor
import com.badoo.mvicore.element.PostProcessor
import com.badoo.mvicore.element.Reducer
import com.badoo.mvicore.feature.BaseFeature
import dfilyustin.translate.model.Translation
import dfilyustin.translate.repository.TranslateRepository
import dfilyustin.translate.utils.RequestState
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

data class TranslateState(
    val query: String? = null,
    val translations: List<Translation>? = null,
    val submitAllowed: Boolean = false,
    val requestState: RequestState = RequestState.Idle
)

sealed class TranslateAction {
    object Init : TranslateAction()
    data class UpdateQuery(val query: String) : TranslateAction()
    data class ScheduleSubmit(val query: String) : TranslateAction()
    object Clear : TranslateAction()
}

sealed class TranslateEffect {
    data class UpdatedQuery(
        val query: String,
        val submitAllowed: Boolean
    ) : TranslateEffect()
    object ClearedResults : TranslateEffect()

    object SubmittedQuery : TranslateEffect()
    object CancelledQuery : TranslateEffect()
    data class ReceivedTranslations(val translations: List<Translation>) : TranslateEffect()
    data class FailedTranslation(val e: Throwable) : TranslateEffect()
}

class TranslateActor(
    private val repository: TranslateRepository,
    private val computationScheduler: Scheduler,
    private val ioScheduler: Scheduler,
    private val uiScheduler: Scheduler
) : Actor<TranslateState, TranslateAction, TranslateEffect> {

    private val submitSubject = PublishSubject.create<String>()
    private val inputSubject = PublishSubject.create<Unit>()

    companion object {
        private const val discardUntilMillis = 300L
        private val isQueryValid = Regex("^[a-zA-Z]{2,}[a-zA-Z ]*$")
    }

    override fun invoke(
        state: TranslateState,
        action: TranslateAction
    ): Observable<out TranslateEffect> {
        return when (action) {
            TranslateAction.Init -> submitSubject
                .debounce(discardUntilMillis, TimeUnit.MILLISECONDS, computationScheduler)
                .switchMap {
                    repository
                        .getTranslation(it)
                        .toObservable()
                        .takeUntil(inputSubject)
                        .map<TranslateEffect>(TranslateEffect::ReceivedTranslations)
                        .mergeWith(inputSubject.map { TranslateEffect.CancelledQuery })
                        .onErrorReturn(TranslateEffect::FailedTranslation)
                        .startWith(TranslateEffect.SubmittedQuery)
                }
                .subscribeOn(ioScheduler)
                .observeOn(uiScheduler)
            is TranslateAction.UpdateQuery -> {
                inputSubject.onNext(Unit)
                Observable.just(
                    TranslateEffect.UpdatedQuery(
                        query = action.query,
                        submitAllowed = isSubmitAllowed(action.query)
                    )
                )
            }
            TranslateAction.Clear -> {
                inputSubject.onNext(Unit)
                Observable.just(TranslateEffect.ClearedResults)
            }
            is TranslateAction.ScheduleSubmit -> {
                submitSubject.onNext(action.query)
                Observable.empty()
            }
        }
    }


    private fun isSubmitAllowed(query: String): Boolean = isQueryValid.matches(query.trim())

}

class TranslateReducer : Reducer<TranslateState, TranslateEffect> {
    override fun invoke(state: TranslateState, effect: TranslateEffect): TranslateState =
        when (effect) {
            is TranslateEffect.UpdatedQuery -> state.copy(
                query = effect.query,
                submitAllowed = effect.submitAllowed
            )
            TranslateEffect.SubmittedQuery -> state.copy(requestState = RequestState.Running)
            TranslateEffect.CancelledQuery -> state.copy(
                requestState = RequestState.Idle
            )
            is TranslateEffect.ReceivedTranslations -> state.copy(
                translations = effect.translations,
                requestState = RequestState.Idle
            )
            is TranslateEffect.FailedTranslation -> state.copy(
                query = null,
                translations = null,
                requestState = RequestState.Failed(effect.e)
            )
            TranslateEffect.ClearedResults -> TranslateState()
        }
}

class TranslatePostProcessor : PostProcessor<TranslateAction, TranslateEffect, TranslateState> {
    override fun invoke(
        action: TranslateAction,
        effect: TranslateEffect,
        state: TranslateState
    ): TranslateAction? = when (effect) {
        is TranslateEffect.UpdatedQuery -> TranslateAction.ScheduleSubmit(effect.query)
            .takeIf { effect.submitAllowed }
        else -> null
    }
}

class TranslateFeature(actor: TranslateActor) :
    BaseFeature<TranslateAction, TranslateAction, TranslateEffect, TranslateState, Any>(
        initialState = TranslateState(),
        actor = actor,
        wishToAction = { it },
        reducer = TranslateReducer(),
        bootstrapper = { Observable.just(TranslateAction.Init) },
        postProcessor = TranslatePostProcessor()
    )