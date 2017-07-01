package com.daanvandenbosch.bonuspunten

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.http.client.fluent.Request
import java.lang.Math.round
import java.time.LocalDate
import java.util.*

val API_BASE_URL = "https://bonus.ly/api/v1"
val API_ACCESS_TOKEN = System.getenv("BONUSLY_API_ACCESS_TOKEN")!!
val SPECIAL_USERS = (System.getenv("BONUSLY_SPECIAL_USERS") ?: "").split(',').toSet()

fun main(args: Array<String>) {
    if (!isLastDayOfMonth()) {
        println("It's not the last day of the month!")
        return
    }

    val me = getUsersMe()
    val givingBalance = me.givingBalance!!
    println("Current giving balance: " + givingBalance)

    val others = getUsers().filter { it.userMode == "normal" && it.id != me.id }
    val specialUsers = others.filter { SPECIAL_USERS.contains(it.username) }.toMutableList()
    val regularUsers = others.filter { !SPECIAL_USERS.contains(it.username) }.toMutableList()
    Collections.shuffle(regularUsers)
    specialUsers.addAll(regularUsers.takeLast(3))
    Collections.shuffle(specialUsers)
    val winners = regularUsers.dropLast(3) + specialUsers

    // All remaining giving balance will be distributed over colleagues and prizes will go down in value linearly.
    // E.g. the person in tenth place will receive one tenth of what the winner receives.
    // prize factor = giving balance / nth triangular number
    // prize for winner i = (n - i + 1) * prize factor
    // where n = the number of colleagues
    val prizeFactor = givingBalance / (winners.size * (1 + winners.size) / 2.0)

    var place = 1
    var givingBalanceLeft = givingBalance

    for (winner in winners) {
        if (givingBalanceLeft <= 0) {
            break
        }

        val prize = minOf(
                maxOf(1, round((winners.size - place + 1) * prizeFactor).toInt()),
                givingBalanceLeft)

        val posStr = place.toString() + (if (place == 1) "ste" else "de")
        val reason = "+$prize @${winner.username} omdat hij/zij de $posStr prijs won in Daan's grote, maandelijkse bonuspuntenloterij! #winnaar"
        println(reason)
        createBonus(reason)

        givingBalanceLeft -= prize
        place += 1
    }

    println("Giving balance left: " + givingBalanceLeft)
}

fun isLastDayOfMonth(): Boolean {
    val now = LocalDate.now()
    val daysLeft = now.lengthOfMonth() - now.dayOfMonth
    println("Date: $now")
    println("Days left: $daysLeft")
    return daysLeft == 0
}

val MAPPER: ObjectMapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .registerModule(KotlinModule())

data class User(val id: String,
                val displayName: String,
                val username: String,
                val email: String,
                val givingBalance: Int?,
                val userMode: String)

data class Bonus(val reason: String)

data class Response<out T>(val success: Boolean,
                           val result: T)

fun getUsers(): List<User> = apiGet(
        "/users?limit=100&include_archived=false&sort=display_name")

fun getUsersMe(): User = apiGet(
        "/users/me?show_financial_data=true")

fun createBonus(reason: String): Bonus = apiPost(
        "/bonuses",
        Bonus(reason))

inline fun <reified T : Any, reified R : Response<T>> apiGet(path: String): T {
    Request
            .Get("$API_BASE_URL$path")
            .addHeader("Authorization", "Bearer $API_ACCESS_TOKEN")
            .execute()
            .returnContent()
            .asStream().use {
        return MAPPER.readValue<R>(it).result
    }
}

inline fun <reified T : Any, reified R : Response<T>> apiPost(path: String, body: T): T {
    Request
            .Post("$API_BASE_URL$path")
            .addHeader("Authorization", "Bearer $API_ACCESS_TOKEN")
            .addHeader("Content-type", "application/json")
            .bodyByteArray(MAPPER.writeValueAsBytes(body))
            .execute()
            .returnContent()
            .asStream().use {
        return MAPPER.readValue<R>(it).result
    }
}
