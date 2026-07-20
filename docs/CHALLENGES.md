# Browser Challenges

Extensions declare challenge policy through `WebChallengeSource`. The host handles detection, WebView, cookies, coordination, and retries.

## WebChallengeSource

```kotlin
interface WebChallengeSource {
    fun webChallengePolicy(failedRequest: Request): WebChallengePolicy
}
```

## WebChallengePolicy

```kotlin
class WebChallengePolicy(
    val mode: InteractiveChallengeMode = InteractiveChallengeMode.HOST_DEFAULT,
    val entryUrl: HttpUrl? = null,
    val verificationUrl: HttpUrl? = null,
    additionalAllowedTopLevelOrigins: Collection<HttpUrl> = emptySet(),
    val initialHeaders: Map<String, String> = emptyMap(),
    val retryPolicy: ChallengeRetryPolicy = ChallengeRetryPolicy.SAFE_METHODS_ONLY,
    val timeoutMillis: Long = 60_000L,
)
```

### InteractiveChallengeMode

| Mode | Behavior |
|---|---|
| `HOST_DEFAULT` | Host decides when to show the WebView |
| `MANUAL_ONLY` | Always show the WebView for user interaction |
| `DISABLED` | Return the original response without challenge handling |

### ChallengeRetryPolicy

| Policy | Behavior |
|---|---|
| `SAFE_METHODS_ONLY` | Retry GET, HEAD, OPTIONS, TRACE |
| `REPLAYABLE_IDEMPOTENT_REQUESTS` | Also retry idempotent POST requests |
| `NEVER` | Resolve state without retrying |

### Validation

Construction rejects:
- URL credentials
- Non-HTTPS values for `entryUrl`, `verificationUrl`, or allowed origins
- More than 16 additional top-level origins
- Timeouts longer than five minutes

Collections are copied after validation — the extension cannot mutate the policy.

### Forbidden Headers

`initialHeaders` rejects: `connection`, `content-length`, `cookie`, `host`, `proxy-authorization`, `set-cookie`, `transfer-encoding`.

## SourceEnvironment Integration

`SourceEnvironment.webChallengeInterceptorFactory` is supplied by the host. `SymeraHttpSource` installs the interceptor returned by that factory automatically.

## WebView Constraints

- HTTPS only for entry and verification URLs
- JavaScript and DOM storage enabled
- File/content access disabled, mixed content denied
- Safe Browsing enabled
- No JavaScript bridge
- SSL errors cancelled, never ignored
- Camera, microphone, geolocation, downloads, untrusted new windows denied
- Third-party cookies enabled only for the challenge session when necessary
- `onRenderProcessGone` destroys the WebView and fails all waiting operations

## POST Retry Rules

POST requests are retried only when `retryPolicy` permits and bodies are not one-shot or duplex. A failed POST must never be converted into a GET.

## User-Initiated Browser

`InteractiveBrowserSource` is separate from challenge interception. It exposes an `InteractiveBrowserRequest` for a visible browser opened explicitly by the user.

```kotlin
class InteractiveBrowserRequest(
    val entryUrl: HttpUrl,
    additionalAllowedTopLevelOrigins: Collection<HttpUrl> = emptySet(),
)
```

The host owns cookies, private-network checks, navigation enforcement, and WebView lifecycle.

## Cloudflare Notes

- Managed Challenge may require user input.
- Turnstile returns a server-validated token, not a clearance cookie.
- Changes in IP, User-Agent, WebView characteristics, or cookie partition can invalidate a session.

References:
- https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
- https://developers.cloudflare.com/cloudflare-challenges/concepts/clearance/
