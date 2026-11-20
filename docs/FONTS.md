# Google Fonts on Android

Motion2Video can save a Google Font for offline preview and MP4 export. Imported HTML starts offline. Font consent is separate from the existing setting for external resources in a WebView.

## Detecting and approving fonts

The Android app scans the current HTML, inline CSS, and stylesheet link attributes before a preview or render. It reads static Google Fonts `/css` and `/css2` stylesheet links and CSS `@import` rules. A bare `font-family` declaration can appear in the summary. Only a matching Google reference can offer a download. The scanner does not execute JavaScript, fetch linked CSS, or follow nested imports. It scans at most 256 KiB of embedded CSS and up to 24 names per category, within the existing 20 MiB input limit. Overlong or unsupported syntax falls back to the existing local behavior. Dynamic imports cannot request approval or use a download command in the native bridge.

The Persian summary identifies bundled, cached, installed and unavailable or unconfirmed families. Bundled fonts take priority. When Android exposes font metadata (API 29+), the app checks family matches for requested names only. A match does not guarantee that the WebView supports that family by name. The result is displayed in the trusted Studio and has no influence on download decisions. Device-font paths, metadata and inventories stay out of imported HTML, the request payload and diagnostics. Older Android versions show unconfirmed names conservatively.

The native dialog shows the eligible names and an upper size estimate calculated locally. Choose **دانلود و ذخیره** to download, **فونت جایگزین** to continue with available fonts, or **لغو** to cancel the preview or render request. Fallback applies during the current session and a newly picked file clears that decision. During an active download, Cancel stops further work and continues using available fonts. Families fully saved before cancellation remain usable. The optional remember checkbox starts unchecked. It remembers only the successfully saved families from that approval. Reuse requires a new explicit import or render action and covers only previously approved families. New families require separate approval. A restored draft or automatic edit preview cannot use remembered consent to start the network on its own.

## Transport and limits

A native worker generates a CSS2 request from approved names and predefined style and weight combinations. It tries a variable request, common upright weights, and finally the default face when the prior request returns HTTP 400. Available styles and character subsets depend on the response. Android or WebView may synthesize styles that are absent from the cached response.

The worker uses HTTPS to exactly `fonts.googleapis.com` for CSS and `fonts.gstatic.com` for font bytes. It rejects redirects, HTTP, custom ports, credentials, IP literals, unexpected paths, and unapproved schemes. It uses normal platform TLS checks, a fixed generic user agent, and disables connection caching and explicit proxies. A process-wide cookie handler causes the request to fail closed. The app supplies no cookies, Referer, identifier or imported URL. Only the recognized properties of valid `@font-face` rules are used. The app regenerates CSS locally with no remote URLs, ignores other rules, and verifies advertised font types, file extensions, signatures and size fields. A malformed face for the requested family fails the family download.

Limits set in `FontDiscovery.java`, `FontConsent.java`, `FontDownload.java` and `FontCache.java` include 8 families per consent dialog, 16 faces per family response, 128 KiB CSS per response, 3 MiB per font file, 12 MiB per family and 100 MiB or 256 faces in the cache. The connection timeout is 5 seconds, the read timeout is 6 seconds, and the batch has a 45-second deadline. The native progress dialog can cancel the worker. Subsequent preview and export use the existing 3-second font readiness cap after page completion. Downloading does not change the render clock or the WebView network setting. Increase limits only after reviewing device memory and disk impact and updating validation.

## What Google can see and how to delete a font

An approved request reveals the requested family and predefined style and weight parameters, the fixed user agent, the timing of the request, and the device’s public IP address to Google. Google also sees the font file requests it serves. The app does not send the HTML, animation, filename, cache contents, installed-font inventory or account data. Each font retains its own license. The Google CSS response does not establish a license for a font, so the app labels the upstream license as unverified. It does not record a claimed license from CSS.

Open **فونت‌ها** in the Studio and tap a saved family, then confirm **پاک کن**. Use **پاک‌کردن همه** to remove all saved families and remembered approvals. **پاک‌کردن اجازه‌ها** removes remembered approvals while keeping the downloaded files. Use the settings switch to disable future downloads and use of the private font cache on new previews and renders. If a preview is already open, reload it to see the change. Deletion waits until the active render ends. Clearing the app’s data also removes the files and consent settings.

The cache lives in app-private files and contains font bytes, requested family and face descriptors, verified byte size, date and SHA-256. Each face ID is derived from the family, weight, style and subset. A local stylesheet alias and synthetic `motion.local/cached-fonts/` routes provide only font IDs in the current document's scope. Each read verifies the file hash and removes corrupted entries. An offscreen stage pins its font scope until teardown, so the settings action cannot disrupt its render.

## Verification

`python3 tools/test_fonts.py` uses a fake transport for policy, consent, limits, cancellation and cache verification. `npm run test:ui` uses the original motion fixture plus a Cairo variant with a cache miss and a seeded test face. The test serves that face through the same native policy with Android shims and does not request Google. The seeded bytes are a test asset aliased as Cairo to test serving and CSS selection. Use the [device checklist](VALIDATION.md) for real Google responses, render behavior and system font matching.

### Phase 9 host results

- Debug and unsigned R8 release APKs built successfully. The debug build used the existing local development key for packaging.
- Font policy and cache tests passed with fake transport. The cache test verified persistence, SHA-256 corruption removal, scope isolation, and active-render deletion protection.
- The unmodified fixture loaded Vazirmatn and Orbitron and returned frame acknowledgements 41 and 42 with zero console errors or outbound requests. The Cairo variant produced nonblank frames both before and after caching. Before caching it received the expected 403 and used fallback. With seeded cache it loaded the Cairo test face with zero console errors, blocked requests or outbound requests.
- UI, CSP and network isolation, clock, all 72 byte-identical YUV cases, 246 bridge/request checks, asset integrity, privacy and compiled APK manifest checks passed. The asset check covered 38 files and 14 component records.
- Google’s production responses and physical Android behavior were not exercised. The host route shim does not exercise Android’s TLS stack, font decoder, native consent dialog or OEM WebView. The device checklist includes font-request compatibility, cold offline reuse, application lifecycle and packet inspection.
