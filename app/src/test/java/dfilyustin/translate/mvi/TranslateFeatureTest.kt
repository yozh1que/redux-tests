package dfilyustin.translate.mvi

import dfilyustin.translate.model.Translation
import dfilyustin.translate.repository.TranslateRepository
import dfilyustin.translate.utils.RequestState
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Matchers.anyString
import org.mockito.Mockito
import java.util.concurrent.TimeUnit

class TranslateFeatureTest {

    companion object {
        private val translations = listOf(
            Translation(
                originalText = "translation",
                meanings = listOf(
                    Translation.Meaning(
                        translatedText = "translated",
                        imageUrl = "url"
                    )
                )
            )
        )
        private const val validQuery = "Query one"
        private const val wrongSymbols = "Query 11"
        private const val shortQuery = "Q"
        private val error = IllegalArgumentException("error")
    }

    private lateinit var feature: TranslateFeature
    private lateinit var mockRepository: TranslateRepository
    private lateinit var testScheduler: TestScheduler
    private lateinit var observer: TestObserver<TranslateState>

    @Before
    fun before() {
        observer = TestObserver()
        testScheduler = TestScheduler()
        mockRepository = Mockito.mock(TranslateRepository::class.java)
        feature = TranslateFeature(
            actor = TranslateActor(
                repository = mockRepository,
                ioScheduler = Schedulers.trampoline(),
                uiScheduler = Schedulers.trampoline(),
                computationScheduler = testScheduler
            )
        )

        feature.subscribe(observer)
    }

    @Test
    fun `scheduled debounced request retrieves translations once`() {
        Mockito.`when`(mockRepository.getTranslation(anyString())).thenReturn(
            Single.just(
                translations
            )
        )

        feature.accept(TranslateAction.UpdateQuery(validQuery))
        testScheduler.advanceTimeBy(10L, TimeUnit.MILLISECONDS)
        feature.accept(TranslateAction.UpdateQuery("$validQuery q"))
        testScheduler.advanceTimeBy(10L, TimeUnit.MILLISECONDS)
        feature.accept(TranslateAction.UpdateQuery(validQuery))
        fulfillDebounceRequirements()

        assertEquals(
            listOf(
                TranslateState(),
                TranslateState(query = validQuery, submitAllowed = true),
                TranslateState(query = "$validQuery q", submitAllowed = true),
                TranslateState(query = validQuery, submitAllowed = true),
                TranslateState(
                    query = validQuery,
                    submitAllowed = true,
                    requestState = RequestState.Running
                ),
                TranslateState(
                    query = validQuery,
                    submitAllowed = true,
                    requestState = RequestState.Idle,
                    translations = translations
                )
            ),
            observer.values()
        )
    }

    @Test
    fun `unsuccessful request is reflected in state`() {
        Mockito.`when`(mockRepository.getTranslation(anyString())).thenReturn(Single.error(error))

        feature.accept(TranslateAction.UpdateQuery(validQuery))
        fulfillDebounceRequirements()

        assertEquals(
            listOf(
                TranslateState(),
                TranslateState(query = validQuery, submitAllowed = true),
                TranslateState(
                    query = validQuery,
                    submitAllowed = true,
                    requestState = RequestState.Running
                ),
                TranslateState(submitAllowed = true, requestState = RequestState.Failed(error))
            ),
            observer.values()
        )
    }

    @Test
    fun `invalid queries are skipped`() {
        feature.accept(TranslateAction.UpdateQuery(wrongSymbols))
        fulfillDebounceRequirements()
        feature.accept(TranslateAction.UpdateQuery(shortQuery))
        fulfillDebounceRequirements()

        assertEquals(
            listOf(
                TranslateState(),
                TranslateState(query = wrongSymbols),
                TranslateState(query = shortQuery)
            ),
            observer.values()
        )
    }

    @Test
    fun `subsequent queries cancel ongoing requests`() {
        val apiTestScheduler = TestScheduler()
        val observer = TestObserver<TranslateState>()
        Mockito.`when`(mockRepository.getTranslation(anyString()))
            .thenReturn(Single.timer(3L, TimeUnit.SECONDS, apiTestScheduler).map { translations })
        val feature = TranslateFeature(
            actor = TranslateActor(
                repository = mockRepository,
                uiScheduler = Schedulers.trampoline(),
                ioScheduler = Schedulers.trampoline(),
                computationScheduler = testScheduler
            )
        )
        feature.subscribe(observer)

        feature.accept(TranslateAction.UpdateQuery(validQuery))
        fulfillDebounceRequirements()
        // cancels the ongoing request
        feature.accept(TranslateAction.UpdateQuery("$validQuery q"))
        fulfillDebounceRequirements()
        // completes second ongoing request
        apiTestScheduler.advanceTimeBy(10L, TimeUnit.SECONDS)

        assertEquals(
            listOf(
                TranslateState(),
                TranslateState(query = validQuery, submitAllowed = true),
                TranslateState(
                    query = validQuery,
                    submitAllowed = true,
                    requestState = RequestState.Running
                ),
                // query update cancels running request
                TranslateState(
                    query = validQuery,
                    submitAllowed = true,
                    requestState = RequestState.Idle
                ),
                TranslateState(
                    query = "$validQuery q",
                    submitAllowed = true,
                    requestState = RequestState.Idle
                ),
                TranslateState(
                    query = "$validQuery q",
                    submitAllowed = true,
                    requestState = RequestState.Running
                ),
                TranslateState(
                    query = "$validQuery q",
                    submitAllowed = true,
                    requestState = RequestState.Idle,
                    translations = translations
                )
            ),
            observer.values()
        )
    }

    private fun fulfillDebounceRequirements() {
        // wait until debounce windows expires to emit an api request
        testScheduler.advanceTimeBy(1L, TimeUnit.SECONDS)
    }
}
