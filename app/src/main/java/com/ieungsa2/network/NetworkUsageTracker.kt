package com.ieungsa2.network

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.NetworkCapabilities
import android.util.Log

class NetworkUsageTracker(private val context: Context) {

    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun queryNetworkUsage(uid: Int, startTime: Long, endTime: Long): Long {
        var total = 0L
        // 와이파이와 셀룰러 데이터를 합산하여 누락 방지
        total += queryByNetworkType(NetworkCapabilities.TRANSPORT_WIFI, uid, startTime, endTime)
        total += queryByNetworkType(NetworkCapabilities.TRANSPORT_CELLULAR, uid, startTime, endTime)
        return total
    }

    private fun queryByNetworkType(type: Int, uid: Int, start: Long, end: Long): Long {
        var usage = 0L
        try {
            val stats = networkStatsManager.queryDetailsForUid(type, null, start, end, uid)
            if (stats != null) {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    usage += bucket.rxBytes + bucket.txBytes
                }
                stats.close() // 자원 반납 필수
            }
        } catch (e: Exception) {
            Log.e("NetworkUsage", "조회 실패 ($type): ${e.message}")
        }
        return usage
    }
}
