@file:Suppress("HasPlatformType")

package com.fueledbycaffeine.bunnypedia.database

import android.content.Context
import com.fueledbycaffeine.bunnypedia.R
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

class Database(context: Context) {
  data class UnreadableDeck(val deck: Deck, override val cause: Throwable): Exception()
  object CardNotFound: Exception()

  companion object {
    private val moshi by lazy {
      Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }
    private val adapter by lazy {
      val type = Types.newParameterizedType(List::class.java, Card::class.java)
      moshi.adapter<List<Card>>(type)
    }
  }

//  private val cards: List<Card>
//
//  init {
//    val json = context.resources.openRawResource(R.raw.cards).use { stream ->
//      val reader = BufferedReader(InputStreamReader(stream))
//      reader.readText()
//    }
//
//    cards = adapter.fromJson(json)!!
//  }
//
//  val allCards: List<Card> get() { return cards }
//
//  fun getCard(id: Int): Card? {
//    val index = cards.binarySearch { it.id.compareTo(id) }
//    return if (index < 0) {
//      null
//    } else {
//      cards[index]
//    }
//  }

  val cardsSubject = BehaviorSubject.createDefault(emptyList<Card>())

  init {
    readData(context)
      .subscribeBy(
        onSuccess = {
          cardsSubject.onNext(it)
        },
        onError = Timber::e
      )
  }

  fun getAllCards(): Observable<List<Card>> {
    return cardsSubject.share()
  }

  fun getCard(id: Int): Card? {
    val cards = cardsSubject.value
    val index = cards.binarySearch { it.id.compareTo(id) }
    return if (index < 0) {
      null
    } else {
      cards[index]
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun readData(context: Context): Single<List<Card>> {
    val sources = Deck.values().map { deck ->
      Single.fromCallable { readData(context, deck) }
        .subscribeOn(Schedulers.io())
    }

    return Single.zip(sources) { data ->
      val cards = data
        .flatMap { it as List<Card> }
        .sortedBy { it.id }
      cards
    }
  }

  private fun readData(context: Context, deck: Deck): List<Card> {
    val json = context.resources.openRawResource(deck.data).use { stream ->
      val reader = BufferedReader(InputStreamReader(stream))
      reader.readText()
    }
    return try {
      adapter.fromJson(json)!!
    } catch (e: Exception) {
      throw UnreadableDeck(deck, e)
    }
  }
}