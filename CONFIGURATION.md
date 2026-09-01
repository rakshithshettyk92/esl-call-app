# ESL Call App configuration

The Android build reads these values from either Gradle project properties or
environment variables. Gradle properties take precedence. Put local values in
your user-level `~/.gradle/gradle.properties`; do not commit production secrets.

| Property | Default | Purpose |
|---|---:|---|
| `RELAY_URL` | `https://20.121.68.137` | Relay base URL |
| `NETWORK_CONNECT_TIMEOUT_SECONDS` | `15` | Connection deadline |
| `NETWORK_READ_TIMEOUT_SECONDS` | `30` | Response deadline |
| `AUTH_POLL_INTERVAL_SECONDS` | `300` | Relay-session health check interval |
| `ALERT_TIMEOUT_SECONDS` | `60` | Active-call response window |
| `KEEP_READY_SCREEN_ON` | `true` | Prevent sleep while the Ready screen is visible |

Example:

```properties
RELAY_URL=https://20.121.68.137
NETWORK_CONNECT_TIMEOUT_SECONDS=15
NETWORK_READ_TIMEOUT_SECONDS=30
AUTH_POLL_INTERVAL_SECONDS=300
ALERT_TIMEOUT_SECONDS=60
KEEP_READY_SCREEN_ON=true
```

Each associate has an independent relay session backed by their own AIMS access
and refresh tokens. The APK never contains the AIMS webhook secret. Signing out
revokes only that phone's relay session and device registration.

After installation, the Admin > Device Alert Settings screen can override the
alert response window, relay health-check interval, and Ready-screen awake
behavior on that phone without rebuilding the APK. It also provides an
automatic device sign-out dropdown: Never (recommended), 1 hour, 2 hours,
12 hours, or 24 hours. This timeout signs out only that associate and never
affects another device.
