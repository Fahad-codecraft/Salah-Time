package com.example.salahtime

data class Hijri(
    val adjustedHolidays: List<Any?>,
    val date: String,
    val day: String,
    val designation: Designation,
    val format: String,
    val holidays: List<Any?>,
    val method: String,
    val month: MonthX,
    val weekday: WeekdayX,
    val year: String
)