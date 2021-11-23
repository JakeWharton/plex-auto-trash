# Plex Auto Trash

Automatically empty the trash in all of your Plex libraries.

If you disable [automatic trash emptying](https://support.plex.tv/articles/200289326-emptying-library-trash/) (and you probably should) trash still accumulates and must be emptied manually.
This tool will automatically empty the trash of every library using two simple rules:
- Do not empty trash if the library is currently scanning.
- Do not empty trash until X minutes have passed since last scan (where X is configurable).

Available as a standalone binary and Docker container.


## Usage

You will need a Plex authentication token to use this tool.
See [Plex's documentation](https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/) on how to obtain yours.

From there, you can access the tool in one of two ways:

 * [Binary](#binary)
 * [Docker](#docker)

### Binary

Install on Mac with:
```
$ brew install JakeWharton/repo/plex-auto-trash
```
which will put the `plex-auto-trash` on your shell path (assuming Homebrew is set up correctly).

For other platforms, download ZIP from
[latest release](https://github.com/JakeWharton/plex-auto-trash/releases/latest)
and run `bin/plex-auto-trash` or `bin/plex-auto-trash.bat`.

#### Command-line instructions

```
Usage: plex-auto-trash [OPTIONS] [LIBRARY]...

  Empty the trash in all of your Plex libraries.

Options:
  --base-url URL          Base URL of Plex server web interface (e.g.,
                          http://plex:32400/)
  --token TOKEN           Plex authentication token. See:
                          https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/
  --scan-idle MINUTES     Minimum amount of time (in minutes) since a library
                          scan to empty its trash (default: 5)
  --exclude-library NAME  Name of libraries to exclude. Mutually exclusive
                          with LIBRARY arguments.
  -h, --help              Show this message and exit

Arguments:
  LIBRARY  Name of libraries to scan. All libraries will be scanned if none
           specified. Mutually exclusive with --exclude-library
```

The `--base-url` and `--token` arguments are required.

When run, the tool will traverse all of your Plex libraries to get their folder paths. Then, it will
obtain every file in those paths and compare it to items in that Plex library. Any files which are
not indexed by Plex will be output, and the command will have an exit code of 1.

```
$ plex-auto-trash --base-url http://plexms:32400/ --token MY_TOKEN
[1/2] Emptying trash: Movies... Done
[2/2] Emptying trash: TV... Done
```

### Docker

A container which runs the binary is available from Docker Hub and GitHub Container Registry.

* `jakewharton/plex-auto-trash`
	[![Docker Image Version](https://img.shields.io/docker/v/jakewharton/plex-auto-trash?sort=semver)][hub]
	[![Docker Image Size](https://img.shields.io/docker/image-size/jakewharton/plex-auto-trash)][hub]

* `ghcr.io/jakewharton/plex-auto-trash`

[hub]: https://hub.docker.com/r/jakewharton/plex-auto-trash/

Start this container and point it at your Plex server with the `PLEX_BASE_URL` and `PLEX_TOKEN` environment variables.

```
$ docker run -d \
    -e "PLEX_BASE_URL=http://plexms:32400" \
    -e "PLEX_TOKEN=abcdef123456" \
    jakewharton/plex-auto-trash:1
```

For Docker Compose, add it as an additional service:
```yaml
services:
  plex-auto-trash:
    container_name: plex-auto-trash
    image: jakewharton/plex-auto-trash:1
    restart: unless-stopped
    environment:
      - "PLEX_BASE_URL=http://plexms:32400"
      - "PLEX_TOKEN=abcdef123456"
```

The container will empty trash at 12 minutes past the hour, every hour by default.
This should hopefully avoid collision with other tools and scheduled library scans.
To change when it runs, specify the `CRON` environment variable with a valid cron specifier.
For help creating a valid cron specifier, visit [cron.help][cron].

[cron]: https://cron.help/#0_*_*_*_*

The default minimum time since last scan (called "idle time") is 5 minutes.
Specify an integer value in the `SCAN_IDLE` environment variable to change this value.

To be notified when sync is failing visit https://healthchecks.io, create a check, and specify
the ID to the container using the `HEALTHCHECK_ID` environment variable.


## Development

To run the latest code build with `./gradlew installDist`.  This will put the application into
`build/install/plex-auto-trash/`. From there you can use the
[command-line instructions](#command-line-instructions) to run.

The Docker containers can be built with `docker build .`, which also runs the full set of checks
as CI would.


# License

    Copyright 2021 Jake Wharton

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
