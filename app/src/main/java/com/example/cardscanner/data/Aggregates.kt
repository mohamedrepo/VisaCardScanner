package com.example.cardscanner.data

/**
 * Aggregation rows for the dashboard (spec §33).
 */
data class StatusCount(val status: String, val n: Int)
data class LabelCount(val label: String, val n: Int)
