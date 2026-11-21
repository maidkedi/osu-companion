package com.osuchat.app.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface OsuAuthService {
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeAuthorizationCode(
        @Field("client_id") clientId: String = OAuthConfig.CLIENT_ID.trim(),
        @Field("client_secret") clientSecret: String = OAuthConfig.CLIENT_SECRET.trim(),
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("redirect_uri") redirectUri: String = OAuthConfig.REDIRECT_URI
    ): OAuthTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String = OAuthConfig.CLIENT_ID.trim(),
        @Field("client_secret") clientSecret: String = OAuthConfig.CLIENT_SECRET.trim(),
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): OAuthTokenResponse
}

interface OsuApiService {
    @GET("api/v2/me")
    suspend fun getMe(
        @Header("Authorization") bearerToken: String
    ): OsuUserDto

    @GET("api/v2/friends")
    suspend fun getFriends(
        @Header("Authorization") bearerToken: String
    ): List<OsuUserDto>

    @POST("api/v2/friends")
    suspend fun addFriend(
        @Header("Authorization") bearerToken: String,
        @Query("target") targetUserId: Long
    ): Response<Unit>

    @DELETE("api/v2/friends/{user}")
    suspend fun removeFriend(
        @Header("Authorization") bearerToken: String,
        @Path("user") targetUserId: Long
    ): Response<Unit>

    @GET("api/v2/chat/channels")
    suspend fun getChannels(
        @Header("Authorization") bearerToken: String
    ): Response<List<ChatChannelDto>>

    @POST("api/v2/chat/channels")
    suspend fun createChannel(
        @Header("Authorization") bearerToken: String,
        @Body request: OsuCreateChannelRequestDto
    ): Response<ChatChannelDto>

    @GET("api/v2/chat/updates")
    suspend fun getChatUpdates(
        @Header("Authorization") bearerToken: String,
        @Query("since") sinceMessageId: Long? = null,
        @Query("limit") limit: Int = 50
    ): ChatUpdatesResponse

    @GET("api/v2/chat/channels/{channel}/messages")
    suspend fun getChannelMessages(
        @Header("Authorization") bearerToken: String,
        @Path("channel") channelId: Long,
        @Query("limit") limit: Int = 50,
        @Query("since") since: Long? = null,
        @Query("until") until: Long? = null
    ): Response<List<ChatMessageDto>>

    @POST("api/v2/chat/channels/{channel}/messages")
    suspend fun sendChannelMessage(
        @Header("Authorization") bearerToken: String,
        @Path("channel") channelId: Long,
        @Body request: SendChannelMessageRequest
    ): Response<ChatMessageDto>

    @POST("api/v2/chat/new")
    suspend fun createPmAndSend(
        @Header("Authorization") bearerToken: String,
        @Body request: CreatePmRequest
    ): Response<CreatePmResponse>

    @PUT("api/v2/chat/channels/{channel}/users/{user}")
    suspend fun joinChannel(
        @Header("Authorization") bearerToken: String,
        @Path("channel") channelId: Long,
        @Path("user") userId: Long
    ): ChatChannelDto

    @DELETE("api/v2/chat/channels/{channel}/users/{user}")
    suspend fun leaveChannel(
        @Header("Authorization") bearerToken: String,
        @Path("channel") channelId: Long,
        @Path("user") userId: Long
    ): Response<Unit>

    @PUT("api/v2/chat/channels/{channel}/mark-as-read/{message}")
    suspend fun markAsRead(
        @Header("Authorization") bearerToken: String,
        @Path("channel") channelId: Long,
        @Path("message") messageId: Long
    ): Response<Unit>

    @GET("api/v2/users/{user}")
    suspend fun getUser(
        @Header("Authorization") bearerToken: String,
        @Path("user") userIdOrName: String,
        @Query("key") keyType: String? = null
    ): OsuUserDto

    @GET("api/v2/users/{user}/{mode}")
    suspend fun getUserWithMode(
        @Header("Authorization") bearerToken: String,
        @Path("user") userIdOrName: String,
        @Path("mode") mode: String,
        @Query("key") keyType: String? = null
    ): OsuUserDto

    @GET("api/v2/beatmaps/{beatmap}")
    suspend fun getBeatmap(
        @Header("Authorization") bearerToken: String,
        @Path("beatmap") beatmapId: Long
    ): OsuBeatmapDto

    @GET("api/v2/beatmapsets/{beatmapset}")
    suspend fun getBeatmapset(
        @Header("Authorization") bearerToken: String,
        @Path("beatmapset") beatmapsetId: Long
    ): OsuBeatmapsetDto

    @GET("api/v2/beatmaps/{beatmap}/scores")
    suspend fun getBeatmapScores(
        @Header("Authorization") bearerToken: String,
        @Path("beatmap") beatmapId: Long,
        @Query("mode") mode: String? = null
    ): OsuBeatmapScoresResponseDto

    @GET("api/v2/beatmaps/{beatmap}/scores/users/{user}/all")
    suspend fun getUserBeatmapScores(
        @Header("Authorization") bearerToken: String,
        @Path("beatmap") beatmapId: Long,
        @Path("user") userId: Long,
        @Query("mode") mode: String? = null
    ): OsuUserBeatmapScoresResponseDto

    @GET("api/v2/users/{user}/scores/{type}")
    suspend fun getUserScores(
        @Header("Authorization") bearerToken: String,
        @Path("user") userId: Long,
        @Path("type") type: String,
        @Query("include_fails") includeFails: Int = 1,
        @Query("mode") mode: String? = null,
        @Query("limit") limit: Int = 15,
        @Query("offset") offset: Int = 0
    ): List<OsuScoreDto>

    @GET("api/v2/beatmapsets/search")
    suspend fun searchBeatmapsets(
        @Header("Authorization") bearerToken: String,
        @Query("q") query: String? = null,
        @Query("s") status: String? = null,
        @Query("m") modeInt: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("g") genreId: Int? = null,
        @Query("l") languageId: Int? = null,
        @Query("e") extras: String? = null,
        @Query("c") general: String? = null
    ): OsuBeatmapsetSearchResponseDto

    @GET("api/v2/rankings/{mode}/{type}")
    suspend fun getRankings(
        @Header("Authorization") bearerToken: String,
        @Path("mode") mode: String = "osu",
        @Path("type") type: String = "performance",
        @Query("country") country: String? = null,
        @Query("cursor[page]") page: Int? = null
    ): OsuRankingsResponseDto

    @GET("api/v2/news")
    suspend fun getNews(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 12
    ): OsuNewsResponseDto

    @GET("api/v2/spotlights")
    suspend fun getSpotlights(
        @Header("Authorization") bearerToken: String
    ): OsuSpotlightsResponseDto

    @GET("api/v2/seasonal-backgrounds")
    suspend fun getSeasonalBackgrounds(
        @Header("Authorization") bearerToken: String? = null
    ): OsuSeasonalBackgroundsResponse

    @GET("api/v2/search")
    suspend fun searchUsers(
        @Header("Authorization") bearerToken: String,
        @Query("mode") mode: String = "user",
        @Query("query") query: String
    ): OsuSearchResponseDto

    @GET("api/v2/matches/{match}")
    suspend fun getMatch(
        @Path("match") matchId: Long,
        @Query("before") before: Long? = null,
        @Query("after") after: Long? = null,
        @Query("limit") limit: Int = 100
    ): Response<OsuMatchResponseDto>
}

interface PublicBeatmapApiService {
    @GET("api/v2/b/{beatmap}")
    suspend fun getBeatmap(
        @Path("beatmap") beatmapId: Long
    ): OsuBeatmapDto

    @GET("api/v2/s/{beatmapset}")
    suspend fun getBeatmapset(
        @Path("beatmapset") beatmapsetId: Long
    ): OsuBeatmapsetDto

    @GET("api/v2/search")
    suspend fun searchBeatmapsets(
        @Query("q") query: String = "",
        @Query("m") modeInt: Int? = null,
        @Query("limit") limit: Int = 20
    ): List<OsuBeatmapsetDto>
}

interface NerinyanApiService {
    @GET("search")
    suspend fun searchBeatmapsets(
        @Query("q") query: String = "",
        @Query("m") modeInt: Int? = null,
        @Query("ps") pageSize: Int = 20
    ): List<OsuBeatmapsetDto>
}
