# Browser Challenges

Challenge handling belongs to the host. Extensions declare policy through `WebChallengeSource`.

## Detection

`SourceEnvironment.webChallengeInterceptorFactory` is supplied by the host. `SymeraHttpSource` installs only the interceptor returned by that factory. Implementing `WebChallengeSource` alone does not install challenge behavior.

`CloudflareChallengeDetector` checks:
1. `cf-mitigated: challenge` response header → challenge.
2. HTTP 403/503 + `Server: cloudflare` + `text/html` content type + known markers in a 64 KiB body preview → challenge.

`WebChallengeInterceptor` closes the response and throws `WebChallengeRequiredException`. It does not block an OkHttp thread.

## WebChallengeSource

```kotlin
interface WebChallengeSource {
    fun webChallengePolicy(failedRequest: Request): WebChallengePolicy
}
```

## WebChallengePolicy

```kotlin
data class WebChallengePolicy(
    val mode: InteractiveChallengeMode = InteractiveChallengeMode.HOST_DEFAULT,
    val entryUrl: HttpUrl? = null,                    // must be HTTPS
    val verificationUrl: HttpUrl? = null,             // must be HTTPS
    val allowedTopLevelHosts: Set<String> = emptySet(),
    val initialHeaders: Map<String, String> = emptyMap(),
    val retryPolicy: ChallengeRetryPolicy = ChallengeRetryPolicy.SAFE_METHODS_ONLY,
    val timeoutMillis: Long = 60_000L,
)
```

`InteractiveChallengeMode`: `HOST_DEFAULT` (host decides), `MANUAL_ONLY` (always show WebView), `DISABLED` (return original response).

`ChallengeRetryPolicy`: `SAFE_METHODS_ONLY` (GET/HEAD/OPTIONS/TRACE), `REPLAYABLE_IDEMPOTENT_REQUESTS` (also retryable POST), `NEVER` (resolve state, do not retry).

### Forbidden Headers

`initialHeaders` rejects: `connection`, `content-length`, `cookie`, `host`, `proxy-authorization`, `set-cookie`, `transfer-encoding`.

## Host Flow

1. Catch `WebChallengeRequiredException` at the source operation boundary.
2. Coalesce concurrent requests by source + origin + User-Agent into one challenge session.
3. Present a host-owned WebView when the challenge requires user input.
4. Use the failed request's User-Agent and a shared cookie store.
5. Load only the configured entry host and explicitly approved top-level hosts.
6. Verify clearance with a GET using a client that does not recursively trigger challenge UI.
7. Retry the original source operation once. A second challenge terminates as a loop.

POST requests are retried only when `retryPolicy` permits and bodies are not one-shot or duplex. The host must not convert a failed POST into a GET.

## WebView Constraints

- HTTPS only for entry and verification URLs.
- JavaScript and DOM storage enabled for the challenge page.
- File/content access disabled, mixed content denied.
- Safe Browsing enabled.
- No JavaScript bridge.
- SSL errors cancelled, never ignored.
- Camera, microphone, geolocation, downloads, untrusted new windows denied.
- Third-party cookies enabled only for the challenge session when necessary.
- `onRenderProcessGone` destroys the WebView and fails all waiting operations.

`JavaScriptEngine` is a separate host-provided extraction abstraction. The SDK supplies no WebView implementation, and the abstraction must not be used as an invisible challenge coordinator.

## Cloudflare Limitations

Managed Challenge may require user input. Turnstile returns a server-validated one-time token, not a clearance cookie. Changes in IP, User-Agent, WebView characteristics, or cookie partition can invalidate a session.

References:
- https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
- https://developers.cloudflare.com/cloudflare-challenges/concepts/clearance/
