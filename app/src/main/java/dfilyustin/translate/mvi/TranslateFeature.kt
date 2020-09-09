package dfilyustin.translate.mvi

import com.badoo.mvicore.element.Actor
import com.badoo.mvicore.element.Reducer
import com.badoo.mvicore.feature.ActorReducerFeature
import dfilyustin.translate.model.Translation
import dfilyustin.translate.repository.TranslateRepository
import dfilyustin.translate.utils.RequestState
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.subjects.PublishSubject

data class TranslateState(
    val query: String? = null,
    val translation: Translation? = null,
    val submitAllowed: Boolean = false,
    val requestState: RequestState = RequestState.Idle
)

sealed class TranslateAction {
    data class UpdateQuery(val query: String) : TranslateAction()
    object Submit : TranslateAction()
}

sealed class TranslateEffect {
    data class UpdatedQuery(
        val query: String,
        val submitAllowed: Boolean
    ) : TranslateEffect()

    object SubmittedQuery : TranslateEffect()
    object CancelledQuery : TranslateEffect()
    data class ReceivedTranslation(val translation: Translation) : TranslateEffect()
    data class FailedTranslation(val e: Throwable) : TranslateEffect()
}

class TranslateActor(
    private val repository: TranslateRepository,
    private val ioScheduler: Scheduler,
    private val uiScheduler: Scheduler
) : Actor<TranslateState, TranslateAction, TranslateEffect> {

    private val submitSubject = PublishSubject.create<Unit>()

    companion object {
        private val isQueryValid = Regex("^[a-zA-Z]{2,}[a-zA-Z ]*$")
    }

    override fun invoke(
        state: TranslateState,
        action: TranslateAction
    ): Observable<out TranslateEffect> = when (action) {
        is TranslateAction.UpdateQuery -> Observable.fromCallable {
            TranslateEffect.UpdatedQuery(
                query = action.query,
                submitAllowed = isSubmitAllowed(action.query)
            )
        }.doOnNext { submitSubject.onNext(Unit) }
        TranslateAction.Submit -> state.query?.takeIf { state.submitAllowed }?.let {
            repository
                .getTranslation(it)
                .toObservable()
                .takeUntil(submitSubject)
                .map<TranslateEffect>(TranslateEffect::ReceivedTranslation)
                .mergeWith(submitSubject.map { TranslateEffect.CancelledQuery })
                .onErrorReturn(TranslateEffect::FailedTranslation)
                .startWith(TranslateEffect.SubmittedQuery)
        } ?: Observable.empty<TranslateEffect>()
    }
        .subscribeOn(ioScheduler)
        .observeOn(uiScheduler)

    private fun isSubmitAllowed(query: String): Boolean = isQueryValid.matches(query.trim())

}

class TranslateReducer : Reducer<TranslateState, TranslateEffect> {
    override fun invoke(state: TranslateState, effect: TranslateEffect): TranslateState =
        when (effect) {
            is TranslateEffect.UpdatedQuery -> state.copy(query = effect.query, submitAllowed = effect.submitAllowed)
            TranslateEffect.SubmittedQuery -> state.copy(requestState = RequestState.Running)
            TranslateEffect.CancelledQuery -> state.copy(
                requestState = RequestState.Idle
            )
            is TranslateEffect.ReceivedTranslation -> state.copy(
                translation = effect.translation,
                requestState = RequestState.Idle
            )
            is TranslateEffect.FailedTranslation -> state.copy(
                query = null,
                translation = null,
                requestState = RequestState.Failed(effect.e)
            )

        }
}

class TranslateFeature(actor: TranslateActor) :
    ActorReducerFeature<TranslateAction, TranslateEffect, TranslateState, Any>(
        initialState = TranslateState(),
        actor = actor,
        reducer = TranslateReducer()
    )