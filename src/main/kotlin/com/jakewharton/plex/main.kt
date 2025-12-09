@file:JvmName("Main")

package com.jakewharton.plex

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
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
import io.github.kevincianfarini.cardiologist.PulseBackpressureStrategy.Companion.SkipNext
import io.github.kevincianfarini.cardiologist.PulseSchedule
import io.github.kevincianfarini.cardiologist.schedulePulse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import okhttp3.logging.HttpLoggingInterceptor.Level.BODY
import okhttp3.logging.HttpLoggingInterceptor.Level.NONE

private class EmptyTrashCommand(
	private val clock: Clock,
	private val timeZone: TimeZone,
) : SuspendingCliktCommand("plex-auto-trash") {
	override fun help(context: Context) = "Empty the trash in all of your Plex libraries."

	private val baseUrl by option(metavar = "URL", envvar = "PLEX_AUTO_TRASH_BASE_URL")
		.help("Base URL of Plex server web interface (e.g., http://plex:32400/)")
		.convert { it.toHttpUrl() }
		.required()

	private val token by option(metavar = "TOKEN", envvar = "PLEX_AUTO_TRASH_TOKEN")
		.help("Plex authentication token. See: https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/")
		.required()
		.validate { it.isNotBlank() }

	private val scanIdle by option(metavar = "MINUTES", envvar = "PLEX_AUTO_TRASH_IDLE_MINUTES")
		.help("Minimum amount of time (in minutes) since a library scan to empty its trash (default: 5)")
		.convert { it.toLong().minutes }
		.default(5.minutes)

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

	private val schedule by option("--cron", metavar = "expression", envvar = "PLEX_AUTO_TRASH_CRON")
		.help("Run command forever and perform sync on this schedule")
		.convert { PulseSchedule.parseCron(it) }

	private val healthCheckId by option("--hc-id", metavar = "id", envvar = "PLEX_AUTO_TRASH_HC_ID")
		.help("ID of Healthchecks.io service to notify")

	private val healthCheckHost by option("--hc-host", metavar = "url", envvar = "PLEX_AUTO_TRASH_HC_HOST")
		.convert { it.toHttpUrl() }
		.default("https://hc-ping.com".toHttpUrl())
		.help("Host of Healthchecks.io service to notify. Requires --hc-id")

	private val debug by option(hidden = true).counted()

	override suspend fun run() {
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

		val healthCheckService = HealthCheckService(healthCheckHost, client)
		val healthCheck = healthCheckId?.let(healthCheckService::newCheck)

		try {
			val schedule = schedule
			if (schedule != null) {
				println("Sync schedule: $schedule")
				val pulse = clock.schedulePulse(schedule, timeZone)
				pulse.beat(strategy = SkipNext) {
					val after = clock.now() - scanIdle
					emptyTrash(plexApi, after, healthCheck)
				}
				error("unreachable") // https://github.com/kevincianfarini/cardiologist/issues/117
			} else {
				val after = clock.now() - scanIdle
				emptyTrash(plexApi, after, healthCheck)
			}
		} finally {
			client.dispatcher.executorService.shutdown()
			client.connectionPool.evictAll()
		}
	}

	private suspend fun emptyTrash(
		plexApi: PlexApi,
		after: Instant,
		healthCheck: HealthCheck?,
	) {
		val started = healthCheck?.start()

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

		started?.complete()
	}
}

suspend fun main(vararg args: String) {
	EmptyTrashCommand(
		clock = Clock.System,
		timeZone = TimeZone.currentSystemDefault(),
	).main(args)
}
