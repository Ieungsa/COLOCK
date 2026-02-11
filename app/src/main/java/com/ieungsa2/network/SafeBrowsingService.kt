package com.ieungsa2.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface SafeBrowsingService {
    @POST("v4/threatMatches:find")
    suspend fun checkUrl(
        @Query("key") apiKey: String,
        @Body request: SafeBrowsingRequest
    ): Response<SafeBrowsingResponse>
}

data class SafeBrowsingRequest(
    val client: ClientInfo = ClientInfo(),
    val threatInfo: ThreatInfo
)

data class ClientInfo(
    val clientId: String = "SmishingGuard",
    val clientVersion: String = "1.0.0"
)

data class ThreatInfo(
    val threatTypes: List<String> = listOf("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
    val platformTypes: List<String> = listOf("ANY_PLATFORM"),
    val threatEntryTypes: List<String> = listOf("URL"),
    val threatEntries: List<ThreatEntry>
)

data class ThreatEntry(val url: String)

data class SafeBrowsingResponse(
    val matches: List<ThreatMatch>? = null
)

data class ThreatMatch(
    val threatType: String,
    val platformType: String,
    val threat: ThreatEntry,
    val threatEntryType: String
)