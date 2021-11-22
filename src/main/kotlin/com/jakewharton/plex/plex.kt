package com.jakewharton.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface PlexApi {
	suspend fun sections(): List<PlexSection>
	suspend fun emptyTrash(sectionKey: String)
}

data class PlexSection(
	val key: String,
	val title: String,
)

class HttpPlexApi(
	private val client: OkHttpClient,
	private val baseUrl: HttpUrl,
	private val token: String,
) : PlexApi {
	private val json = Json {
		ignoreUnknownKeys = true
	}

	override suspend fun sections(): List<PlexSection> {
		val sectionsUrl = baseUrl.newBuilder()
			.addPathSegment("library")
			.addPathSegment("sections")
			.addQueryParameter("X-Plex-Token", token)
			.build()
		val sectionsRequest = Request.Builder()
			.url(sectionsUrl)
			.header("Accept", "application/json")
			.build()
		val sectionsJson = client.newCall(sectionsRequest).awaitString()
		val sectionsResponse =
			json.decodeFromString(PlexResponse.serializer(PlexSections.serializer()), sectionsJson)
		return sectionsResponse.mediaContainer.sections.map {
			PlexSection(it.key, it.title)
		}
	}

	override suspend fun emptyTrash(sectionKey: String) {
		val sectionUrl = baseUrl.newBuilder()
			.addPathSegment("library")
			.addPathSegment("sections")
			.addPathSegment(sectionKey)
			.addPathSegment("emptyTrash")
			.addQueryParameter("X-Plex-Token", token)
			.build()

		val sectionRequest = Request.Builder()
			.url(sectionUrl)
			.put(byteArrayOf().toRequestBody())
			.header("Accept", "application/json")
			.build()

		client.newCall(sectionRequest).awaitString()
	}
}

@Serializable
private data class PlexResponse<T>(
	@SerialName("MediaContainer")
	val mediaContainer: T,
)

@Serializable
private data class PlexSections(
	@SerialName("Directory")
	val sections: List<SectionHeader>,
) {
	@Serializable
	data class SectionHeader(
		val key: String,
		val title: String,
	)
}
