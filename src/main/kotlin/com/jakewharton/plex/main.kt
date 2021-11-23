@file:JvmName("Main")

package com.jakewharton.plex

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.counted
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import okhttp3.logging.HttpLoggingInterceptor.Level.BODY
import okhttp3.logging.HttpLoggingInterceptor.Level.NONE

private class EmptyTrashCommand : CliktCommand(
	name = "plex-auto-trash",
	help = "Empty the trash in all of your Plex libraries.",
) {
	private val baseUrl by option(metavar = "URL")
		.help("Base URL of Plex server web interface (e.g., http://plex:32400/)")
		.convert { it.toHttpUrl() }
		.required()

	private val token by option(metavar = "TOKEN")
		.help("Plex authentication token. See: https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/")
		.required()
		.validate { it.isNotBlank() }

	private val scanIdle by option(metavar = "MINUTES")
		.help("Minimum amount of time (in minutes) since a library scan to empty its trash (default: 5)")
		.convert{ Duration.ofMinutes(it.toLong()) }
		.default(Duration.ofMinutes(5))

	private val libraryExcludes by option("--exclude-library", metavar = "NAME")
		.help("""
			|Name of libraries to exclude.
			|Mutually exclusive with LIBRARY arguments.
			""".trimMargin())
		.multiple()

	private val libraries by argument(name = "LIBRARY")
		.help("""
			|Name of libraries to scan.
			|All libraries will be scanned if none specified.
			|Mutually exclusive with --exclude-library
			""".trimMargin())
		.multiple()

	private val debug by option(hidden = true).counted()

	override fun run() {
		require(libraries.isEmpty() or libraryExcludes.isEmpty()) {
			"Libraries and library excludes are mutually exclusive. Specify neither or one, not both."
		}

		val httpLogger = HttpLoggingInterceptor(::println)
			.apply {
				level = when (debug) {
					0, 1 -> NONE
					2 -> BASIC
					else -> BODY
				}
			}
		val client = OkHttpClient.Builder()
			.addNetworkInterceptor(httpLogger)
			.build()

		val plexApi = HttpPlexApi(client, baseUrl, token)
		val after = Instant.now() - scanIdle

		try {
			runBlocking {
				emptyTrash(plexApi, after)
			}
		} finally {
			client.dispatcher.executorService.shutdown()
			client.connectionPool.evictAll()
		}
	}

	private suspend fun emptyTrash(plexApi: PlexApi, after: Instant) {
		val sections = plexApi.sections()
		val sectionCount = sections.size
		for ((index, section) in sections.withIndex()) {
			if (libraries.isNotEmpty() && section.title !in libraries || section.title in libraryExcludes) {
				if (debug > 0) {
					println("Skipping ${section.title}")
				}
				continue
			}

			print("[${index + 1}/$sectionCount] Emptying trash: ${section.title}...")
			if (section.refreshing) {
				println(" Skipped due to in-progress sync")
			} else if (section.lastScan > after) {
				println(" Skipped due to recent sync")
			} else {
				plexApi.emptyTrash(section.key)
				println(" Done")
			}
		}
	}
}

fun main(vararg args: String) {
	EmptyTrashCommand().main(args)
}
