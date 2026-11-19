# حریم خصوصی / Privacy

به‌روز شده / Updated: 2026-11-19. این متن دربارهٔ سورس بررسی‌شدهٔ `security/phase-5` است؛ APK قدیمی1.4.1 این تغییرات را ندارد. / This describes the reviewed `security/phase-5` source; the legacy1.4.1 APK does not contain these changes.

## فارسی

### چه چیزی روی گوشی می‌ماند؟

ویدیو در خود گوشی ساخته می‌شود. برنامه سرور رندر، حساب کاربری، SDK تبلیغات، آمار استفاده یا گزارشگر خطای متعلق به پروژه ندارد. برنامه به‌صورت خودکار HTML یا ویدیوی شما را برای سازندهٔ پروژه نمی‌فرستد. گزارشگر خطا اضافه نشده است؛ گزینهٔ پنهان یا روشن‌شدن خودکار گزارش هم وجود ندارد.

برای ادامهٔ کار، پیش‌نویس HTML، تنظیمات/وضعیت آخرین رندر و فهرست خروجی‌ها روی دستگاه نگه داشته می‌شوند. فهرست خروجی‌ها شامل نام، مسیر/نشانی فایل، اندازه و زمان ساخت است. درخواست‌های رندر و نسخه‌های کاری ویدیو در حافظهٔ موقت خصوصی برنامه قرار می‌گیرند. درخواست معمولاً پس از پایان یا لغو پاک می‌شود؛ نسخه‌های موقت، کارهای قطع‌شده یا وضعیت قدیمی ممکن است تا پاک‌سازی برنامه/سیستم باقی بمانند. پیش‌نویس رابط هم در فضای محلی WebView/مرورگر ذخیره می‌شود. WebView می‌تواند کش یا کوکی‌های مجاز خودش را داشته باشد؛ کوکی شخص ثالث و فضای DOM محتوای واردشده در اندروید غیرفعال‌اند.

فایل نهایی در `Download/Motion2Video/` ذخیره می‌شود؛ این پوشه خصوصی برنامه نیست. برنامهٔ پخش‌کننده یا فایل‌منیجری که خودتان باز می‌کنید ممکن است فایل را بخواند. پاک‌کردن برنامه، ویدیوهای دانلودشده را لزوماً پاک نمی‌کند. برنامه رمزگذاری جداگانه‌ای برای این فایل‌ها اضافه نمی‌کند.

### چه وقت اینترنت درگیر می‌شود؟

- در اندروید، منابع HTML واردشده پیش‌فرض آفلاین‌اند. فونت‌ها و کتابخانه‌های شناخته‌شده از فایل‌های همراه برنامه پاسخ داده می‌شوند، حتی اگر نشانی آن‌ها شبیه CDN باشد.
- اگر «اجازهٔ منابع آنلاین» را پس از هشدار فارسی فعال کنید، همان کد می‌تواند به سرورها درخواست بفرستد و محتوای خود، اطلاعات قابل‌دسترسی مرورگر یا داده‌های شما را ارسال کند. فقط برای کد مورد اعتماد فعالش کنید. اجازهٔ تازه با بازکردن دوبارهٔ صفحه خاموش است؛ کار شروع‌شده انتخاب همان زمان را نگه می‌دارد.
- فایل HTML مستقلِ مرورگر برای رابط از Tailwind، GSAP و Google Fonts استفاده می‌کند؛ این سرویس‌ها می‌توانند اطلاعات معمول درخواست مثل IP و مرورگر را ببینند. «آفلاین» بودن موشن به معنی آفلاین بودن تمام صفحهٔ مستقل نیست. ضبط مرورگر هم فقط با اجازهٔ انتخاب تب/صفحه انجام می‌شود؛ نسخهٔ اندروید برای رندر از ضبط صفحهٔ واقعی گوشی استفاده نمی‌کند.
- Safe Browsing برای امنیت روشن می‌ماند. برنامه درخواست انصراف از آمار/گزارش خطای WebView را در مانیفست ثبت می‌کند، اما رفتار سیستم، سازندهٔ گوشی و ارائه‌دهندهٔ WebView تابع تنظیمات و سیاست خودشان است. این برنامه تضمین «هیچ بستهٔ شبکه‌ای در کل گوشی ارسال نمی‌شود» نمی‌دهد.
- ارائه‌دهندهٔ ابری انتخاب فایل، گالری/پخش‌کننده و ابزار اختیاری FFmpeg ممکن است طبق انتخاب و تنظیمات خودشان از شبکه استفاده کنند. ابزارهای توسعه/به‌روزرسانی مخزن جدا از برنامه‌اند و خودکار روی گوشی اجرا نمی‌شوند.

### خطاها و پاک‌کردن داده‌ها

برنامه متن خام کنسول HTML را به لاگ اندروید نمی‌فرستد. خطاهای عمومی اندروید از پیام‌های ثابت و بررسی‌شده استفاده می‌کنند؛ متن ناشناختهٔ خطا، نشانی خصوصی و HTML به اعلان‌ها یا پیام‌های جدید وضعیت منتقل نمی‌شوند. مسیر خروجی که عمداً در فهرست دانلود نشان داده می‌شود، بخشی از قابلیت برنامه است. لاگ داخلی سیستم یا ابزار اشکال‌زدایی را نمی‌توان کاملاً از داخل برنامه کنترل کرد. دسترسی اشکال‌زدایی WebView فقط در ساخت debug فعال است. ابزار دسکتاپ خروجی تشخیصی FFmpeg را پیش‌فرض پنهان می‌کند؛ `--debug` آن را با احتمال نمایش مسیرها برای بررسی محلی روشن می‌کند.

برای حذف پیش‌نویس، تاریخچه و دادهٔ خصوصی، ابتدا کار را متوقف کنید و در تنظیمات اندروید، بخش برنامه، **پاک‌کردن حافظه/داده‌ها** را انتخاب کنید. پاک‌کردن کش به‌تنهایی تمام پیش‌نویس‌ها را حذف نمی‌کند. ویدیوهای Downloads را جداگانه با فایل‌منیجر پاک کنید. برای نسخهٔ مستقل، دادهٔ همان سایت/مرورگر را هم پاک کنید. دکمهٔ پاک‌کردن متن و این راهنما تضمین پاک‌سازی امن همهٔ نسخه‌های قدیمی یا بکاپِ سرویس‌های دیگر نیستند.

## English

### What stays on the device?

Rendering runs on your phone. The app has no project-owned render server, account service, advertising/usage analytics SDK or crash reporter. It does not automatically upload your HTML or videos to the project developer. No crash reporter has been added; there is no hidden reporting opt-in or automatic enablement.

The app keeps the HTML draft, last render settings/status and an export list locally. The list includes filenames, paths/URIs, sizes and creation times. Private cache contains render requests and working video copies. Requests are normally removed on completion/cancellation; cached videos, interrupted work or old status can remain until app/OS cleanup. The studio also saves its draft in local WebView/browser storage. WebView can retain its own cache/permitted cookies; native imported content has DOM storage disabled and third-party cookies are blocked.

Final exports are in `Download/Motion2Video/`, outside private app storage. A player/file manager you open can read the selected export. Uninstalling the app does not necessarily delete Downloads. The app does not add its own encryption to these files.

### When can network traffic occur?

- Native imported HTML is offline by default. Known library/font aliases are served from packaged files, even when their URLs resemble CDNs.
- After explicit Persian confirmation, **Allow online resources** lets imported code contact servers and potentially send its content, accessible browser information or your data. Enable it only for trusted code. Fresh Activity sessions start with permission off; an already-started job keeps its approved choice.
- The standalone browser HTML loads Tailwind, GSAP and Google Fonts for its own UI. Those services can receive ordinary request information such as IP/browser details. Offline motion does not make the whole standalone page offline. Browser recording uses the browser's explicit tab/screen selection; native Android rendering does not record the phone's real screen.
- Safe Browsing stays enabled. The app declares WebView usage/crash-metrics opt-out, but Android, device vendors and WebView providers have their own settings/policies. This is not a guarantee of zero packets from the whole device. See [Android's WebView metrics documentation](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview#metrics).
- Cloud document providers, galleries/players and the optional external FFmpeg tool can use the network according to their inputs/settings. Repository update/audit/development tools are separate from the Android app and do not automatically run on the phone.

### Diagnostics and deletion

Native WebView console messages are handled without forwarding their text/source URLs to Android logs. Native public errors use a reviewed fixed catalogue; unknown raw exception text, private paths and HTML are not forwarded to new status messages/notifications. Deliberately displayed export paths remain part of the Downloads feature. This cannot suppress every OS/vendor diagnostic or an explicit debugging session. WebView remote debugging remains debug-build-only. The desktop wrapper hides FFmpeg diagnostics by default; explicit `--debug` enables local diagnostics that can include paths/metadata.

To remove drafts, history and private app data, stop active jobs and use Android **Settings → Apps → Motion2Video → Storage → Clear storage/data**. Clearing cache alone does not clear all drafts. Delete exported Downloads separately. For the standalone version, clear that site's/browser's data too. Clearing the editor is not a promise to erase every saved copy, and this policy does not promise secure erasure or deletion of other services' backups. Old private data/status can remain until overwritten or cleared; legacy error text is filtered before the app displays it.

## Evidence and limits / شواهد و محدودیت‌ها

Source/dependency audit found no app-owned telemetry client or upload endpoint. Real TLS probes test zero imported-resource hits in default mode and demonstrate a synthetic POST body reaching the server only with online consent. Error/desktop diagnostic tests use synthetic fixtures. Compiled APK checks verify the requested metrics opt-out. These are scoped checks, not a full Android packet capture, OS logging audit or proof that every third-party script is safe. Native/device privacy acceptance remains pending; see [audit](docs/AUDIT_REPORT.md) and [validation](docs/VALIDATION.md).

Report concerns through the private process/status described in [SECURITY.md](SECURITY.md). Never put real private HTML, recordings, credentials or raw device logs in a public issue.

Project settings / تنظیمات پروژه: up to 20 named configurations are saved in local WebView/browser storage; they contain output settings, not HTML, audio or online permission. Delete them with the project Delete button or clear application data. / حداکثر ۲۰ تنظیمات نام‌دار، فقط روی دستگاه ذخیره می‌شود؛ با دکمهٔ حذف پروژه یا پاک‌کردن دادهٔ برنامه قابل حذف است.

Render-speed estimates store at most 24 numeric profile measurements locally (codec/size/fps/capture mode), with no HTML, filename, identifier or upload. / حداکثر ۲۴ اندازه‌گیری عددی سرعت رندر، بدون محتوا یا نام فایل، فقط روی دستگاه ذخیره می‌شود.

Optional audio / صدای اختیاری: selected audio is copied to private cache (up to 8 assets and 160 MiB total), and decoder metadata/selected name stay on this device. The Clear temporary audio button deletes these copies when idle; Android can evict cache, requiring re-selection. PCM/AAC/remux scratch is deleted after the job or interrupted-job recovery. No audio upload or crash reporting is added. / صدا، نام انتخابی و داده‌های موقت فقط روی دستگاه‌اند؛ دکمهٔ پاک‌کردن صوت‌های موقت آن‌ها را حذف می‌کند. پاک‌شدن کش توسط اندروید نیاز به انتخاب دوباره دارد؛ هیچ ارسال یا گزارش خطای آنلاین اضافه نشده است.
