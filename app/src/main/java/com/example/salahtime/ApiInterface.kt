package com.example.salahtime

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiInterface {
    @GET("timings/{date}")
    fun getSalahTime(
        @Path("date") date: String,
        @Query("latitude") latitude:String,
        @Query("longitude") longitude:String,
        @Query("method") method:Int,
        @Query("midnightMode") midnightMode:Int
    ) :Call<SalahTimeApp>
}