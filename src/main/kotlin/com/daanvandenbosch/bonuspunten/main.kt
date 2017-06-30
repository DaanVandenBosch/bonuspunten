package com.daanvandenbosch.bonuspunten

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.http.client.fluent.Request
import java.lang.Math.ceil
import java.time.LocalDate
import java.util.*

val API_BASE_URL = "https://bonus.ly/api/v1"
val API_ACCESS_TOKEN = System.getenv("BONUSLY_API_ACCESS_TOKEN")!!
val LOSING_USERS = (System.getenv("BONUSLY_LOSING_USERS") ?: "").split(',').toSet()

fun main(args: Array<String>) {
    if (!isLastDayOfMonth()) {
        println("It's not the last day of the month!")
        return
    }

    val me = getUsersMe()
    println("Current giving balance: " + me.givingBalance)

    val allOthers = getUsers().filter { it.userMode == "normal" && it.id != me.id }
    val others = allOthers.filter { !LOSING_USERS.contains(it.username) }.toMutableList()
    Collections.shuffle(others)
    others.addAll(allOthers.filter { LOSING_USERS.contains(it.username) })

    var givingBalanceLeft = me.givingBalance!!
    var pos = 1

    for (other in others) {
        if (givingBalanceLeft <= 0) {
            break
        }

        val prize = ceil(givingBalanceLeft.toDouble() / 3.0).toInt()

        val posStr = pos.toString() + (if (pos == 1) "ste" else "de")
        val reason = "+$prize @${other.username} omdat hij/zij de $posStr prijs won in Daan's grote, maandelijkse bonuspuntenloterij! #winnaar"
        println(reason)
        createBonus(reason)

        givingBalanceLeft -= prize
        pos += 1
    }

    println("Giving balance left: " + givingBalanceLeft)
}

fun isLastDayOfMonth(): Boolean {
    val now = LocalDate.now()
    val daysLeft = now.lengthOfMonth() - now.dayOfMonth
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
