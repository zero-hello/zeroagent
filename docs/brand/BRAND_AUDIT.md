# ZeroAgent branding review

Product name: ZeroAgent. Product maintainer: Zero. Initial product version: 1.0.0 (versionCode 1). Minimum Android API: fixed 31.

Reviewed and changed: Android localized strings, app label, notifications and permission guidance, About page, export captions, diagnostic reports, OAuth callback pages, request client titles, built-in search display, Web translations/footer/favicon, launcher raster icons, adaptive and monochrome vectors, product README, documentation landing page/icon/hero.

Removed upstream product links from About and sharing text, upstream QQ/Discord invitations, and decorative About logo emoji burst. No ZeroAgent update service is configured. The update check reports the installed version without fetching an upstream APK or showing a failure banner.

Modest UI changes: Material 3 card outer/inner corners and spacing, About icon size/shape and heading spacing. Chat, tools, workflows, local model, voice, document, MCP, OAuth and Web implementations remain.

Technical identifiers intentionally retained: Kotlin namespaces/class names, Gradle convention plugin IDs, serialization names and migrations, protocol keys and existing API endpoints. Provider authorities use ${applicationId}; app ID changes to app.zeroagent (debug: app.zeroagent.debug). These identifiers are not product authorship statements.

Legal and source-history references remain in LICENSE, third-party files, SOURCE_NOTICE.md and upstream engineering/reference documentation. Historical test/build statements do not establish this delivery's validation. Actual Android tests, APK build and metadata/signature checks must be recorded separately after completion.

Logo files: zeroagent-logo.svg, zeroagent-logo.png, zeroagent-icon-masks.png; Android vectors under app/src/main/res/drawable/zeroagent_*.xml. Adaptive safe-zone/mask previews and XML parsing were checked; device rendering remains untested.


Final audit 2026-10-14: corrected remaining ASR descriptions in six locales, OAuth return copy, Mermaid export watermark, bug-report naming, mDNS default name, skill importer client title, Telegram attachment prefix and six bundled skill documents. Added branded provider SVG mapping, removed upstream affiliate parameters without inventing ZeroAgent referral IDs, and rebuilt the APK. APK compiled-string/asset checks pass; actual evidence is in deliverables/DELIVERY_REPORT.md. All upstream product logo artwork is replaced, including notification and Web icons. Serialized keys, registered OAuth metadata and data compatibility identifiers remain unchanged.

Version 1.0.2 extends the review to live glass preferences (off by default), English app UI, task workspace, Settings-only root access and separate signed ABI deliveries. Runtime device behavior and power use require physical-device verification.


Version 1.0.2 review: all production launcher/adaptive/monochrome/notification/Web branding uses the blue-violet Z. The old rabbit loading drawable is removed; a continuous Z animation follows the system animator-duration setting. Fresh installs use a light Material 3 Expressive theme and opaque surfaces. Existing user theme choices remain intact. The new workspace welcome screen has four draft actions, and the task center reads real stored execution states and child runs. Domain detail pages retain their existing controls and full tool records.

The app and embedded Web interface select English according to Zero's final language instruction. Preview-only test text, retired-provider compatibility codecs, voice/model IDs, serialized identifiers and user content are not translated blindly. Retired stock providers remain excluded from the editable catalog while their original settings are archived by the compatibility migration.

Root starts off, is enabled only from Settings after an Android superuser request, and requires confirmation for every command. It is refused in unattended runs and cannot be bypassed by the normal automatic-approval switch. Glass starts off; enabling it uses live Haze sources, adjustable opacity and a power-saver override. Android 13 and later support refraction; Android 12 uses the library's compatible fallback. Device rendering, power use and actual superuser grants remain unverified.

The original AGPL copyright and third-party attribution are retained. Package/class/serialization/protocol identifiers required for compatibility do not claim product authorship. Final build and package evidence must come from the v1.0.2 delivery report rather than historical results above.


### v1.0.3 review
Product namespaces, build conventions, service classes, resource filenames and local Web origins use ZeroAgent. Shared back icons and drawer icon navigation replace text shortcuts. All workspace destinations are visible in Settings; the four redundant setup shortcuts are removed. The welcome uses four compact actions in one row and a small task overview. The Z itself rotates and respects reduced motion. Glass is bounded to individual surfaces, the drawer remains opaque, and Android 12 uses live blur. UI preferences includes the same renderer as an interactive opacity preview.

Legacy database/archive/cloud object names and serialized discriminator readers remain solely for data compatibility. Third-party dependency coordinates and the original source/AGPL notices remain accurate. OAuth uses configured client IDs or dynamic registration; there is no invented hosted ZeroAgent metadata endpoint. An MCP server requiring a hosted client metadata URL needs a configured client ID. Accessibility/notification grants may need to be enabled again because service class names changed. Device rendering, glass performance, animations and installation require physical-device validation.
