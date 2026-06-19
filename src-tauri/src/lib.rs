use events::{
    DownloadAllFavoritesEvent, DownloadSleepingEvent, DownloadSpeedEvent, DownloadTaskEvent,
    ExportCbzEvent, ExportPdfEvent, LogEvent, UpdateDownloadedComicsEvent,
};
use parking_lot::RwLock;
use tauri::{Manager, Wry};

// TODO: 用prelude来消除警告
use crate::commands::*;
use crate::config::Config;
use crate::download_manager::DownloadManager;
use crate::jm_client::JmClient;

mod commands;
mod config;
mod download_manager;
mod errors;
mod events;
mod export;
mod extensions;
mod jm_client;
mod logger;
mod responses;
mod types;
mod utils;

fn generate_context() -> tauri::Context<Wry> {
    tauri::generate_context!()
}

/// Android 诊断：把消息同时打到 logcat(通过 __android_log_write 直接系统调用) 和外部存储文件
/// (/sdcard/Download/jmcomic-boot.log，adb 可直接 pull)。
/// 非 debuggable 的 release app 上，Rust 的 stdout/stderr 不会进 logcat。
/// 注意：不能用 android_logger::init_once，因为它会设置 `log` 全局 logger，
/// 与 logger.rs 里 tracing_subscriber 的 try_init(通过 tracing-log 桥接 log facade)冲突，
/// 导致 SetLoggerError → logger::init 失败 → setup 中断 → 白屏。
/// 这里直接用 NDK 的 __android_log_write，完全不碰 log 全局状态。
#[cfg(target_os = "android")]
mod boot_diag {
    use std::ffi::CString;
    use std::fs::OpenOptions;
    use std::io::Write;

    // NDK liblog 的 __android_log_write(prio, tag, text)
    // prio: 3=DEBUG 4=INFO 5=WARN 6=ERROR
    // liblog.so 是 Android 系统库，android target 默认链接。
    #[link(name = "log")]
    extern "C" {
        fn __android_log_write(prio: i32, tag: *const i8, text: *const i8) -> i32;
    }

    fn logcat(msg: &str) {
        let tag = CString::new("jmcomic-rust").unwrap_or_default();
        // 每行单独写(__android_log_write 不支持多行，按 \n 拆开)
        for line in msg.split('\n') {
            let text = CString::new(line).unwrap_or_default();
            unsafe {
                __android_log_write(4, tag.as_ptr(), text.as_ptr());
            }
        }
    }

    fn append_file(msg: &str) {
        // /sdcard/Download 在大多数 Android 上 app 可写(legacy storage)。
        // 退而求其次写 app cache，但 cache 需要 run-as/debuggable 才能读，
        // 所以优先用外部存储。
        for path in ["/sdcard/Download/jmcomic-boot.log", "/storage/emulated/0/Download/jmcomic-boot.log"] {
            if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(path) {
                let _ = writeln!(f, "{msg}");
                return;
            }
        }
    }

    pub fn trace(msg: impl AsRef<str>) {
        let msg = msg.as_ref();
        logcat(msg);
        append_file(msg);
    }
}

#[cfg(not(target_os = "android"))]
mod boot_diag {
    pub fn trace(msg: impl AsRef<str>) {
        eprintln!("[boot] {}", msg.as_ref());
    }
}

/// 安装 panic hook：把 panic 信息打到 stderr/stdout（Android 上会被 logcat 捕获），
/// 这样白屏/闪退时能在 logcat 里看到真正的 Rust 崩溃原因，而不是只剩一个空进程。
fn install_panic_hook() {
    let default_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        let payload = info
            .payload()
            .downcast_ref::<&str>()
            .copied()
            .or_else(|| info.payload().downcast_ref::<String>().map(String::as_str))
            .unwrap_or("<non-string panic payload>");
        let location = info
            .location()
            .map(|l| format!("{}:{}:{}", l.file(), l.line(), l.column()))
            .unwrap_or_else(|| "<unknown location>".to_string());
        let full = format!(
            "\n==== PANIC ====\nlocation: {location}\npayload: {payload}\nbacktrace below\n================\n{}",
            std::backtrace::Backtrace::force_capture()
        );
        // Android logcat 会捕获 stdout/stderr（标签通常为 Rust 的 tag 或 app tag）。
        // 用多行 + 明显前缀，方便在 logcat 里 grep。
        eprintln!("{full}");
        // 双通道：android_logger 也打一份，防止 stdout 被吞
        boot_diag::trace(&full);
        default_hook(info);
    }));
}

// TODO: 添加Panic Doc
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    boot_diag::trace("run() entered: install_panic_hook");
    install_panic_hook();
    boot_diag::trace("run(): building tauri_specta::Builder");

    let builder = tauri_specta::Builder::<Wry>::new()
        .commands(tauri_specta::collect_commands![
            greet,
            get_config,
            save_config,
            login,
            search,
            get_comic,
            get_favorite_folder,
            get_weekly_info,
            get_weekly,
            get_user_profile,
            create_download_task,
            pause_download_task,
            resume_download_task,
            cancel_download_task,
            download_comic,
            download_all_favorites,
            update_downloaded_comics,
            show_path_in_file_manager,
            sync_favorite_folder,
            get_downloaded_comics,
            export_cbz,
            export_pdf,
            get_logs_dir_size,
            get_synced_comic,
            get_synced_comic_in_favorite,
            get_synced_comic_in_search,
            get_synced_comic_in_weekly,
        ])
        .events(tauri_specta::collect_events![
            DownloadSpeedEvent,
            DownloadSleepingEvent,
            DownloadTaskEvent,
            DownloadAllFavoritesEvent,
            UpdateDownloadedComicsEvent,
            ExportCbzEvent,
            ExportPdfEvent,
            LogEvent,
        ]);

    #[cfg(debug_assertions)]
    builder
        .export(
            specta_typescript::Typescript::default()
                .bigint(specta_typescript::BigIntExportBehavior::Number)
                .formatter(specta_typescript::formatter::prettier)
                .header("// @ts-nocheck"), // 跳过检查
            "../src/bindings.ts",
        )
        .expect("Failed to export typescript bindings");

    boot_diag::trace("run(): calling tauri::Builder::default() + plugins + setup");
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(builder.invoke_handler())
        .setup(move |app| {
            boot_diag::trace("setup() entered");
            builder.mount_events(app);

            // 安卓上 app_data_dir() 偶尔会失败(权限/路径)，一旦失败原来的 `?` 会让
            // 整个 setup 返回 Err，应用直接白屏/不渲染。这里改成容错：
            // 拿不到就用 cache_dir/临时目录兜底，绝不中断 setup。
            let app_data_dir = app.path().app_data_dir().unwrap_or_else(|e| {
                boot_diag::trace(&format!("setup: app_data_dir() failed: {e:?}, fallback to cache_dir"));
                let fallback = app
                    .path()
                    .cache_dir()
                    .or_else(|_| app.path().temp_dir())
                    .unwrap_or_else(|e2| {
                        boot_diag::trace(&format!("setup: cache_dir/temp_dir also failed: {e2:?}, use /tmp"));
                        std::path::PathBuf::from("/tmp/jmcomic-downloader")
                    });
                boot_diag::trace(&format!("setup: fallback app_data_dir: {}", fallback.display()));
                fallback
            });

            if let Err(e) = std::fs::create_dir_all(&app_data_dir) {
                boot_diag::trace(&format!("setup: create_dir_all failed: {e:?}, continue"));
            }

            // Config::new 失败也用默认配置兜底，不让 setup 中断
            let config = match Config::new(app.handle()) {
                Ok(c) => RwLock::new(c),
                Err(e) => {
                    boot_diag::trace(&format!("setup: Config::new failed: {e:?}, use default"));
                    RwLock::new(Config::default(&app_data_dir))
                }
            };
            app.manage(config);

            let jm_client = JmClient::new(app.handle().clone());
            app.manage(jm_client);

            let download_manager = DownloadManager::new(app.handle().clone());
            app.manage(download_manager);

            // logger 失败不影响应用启动
            if let Err(e) = logger::init(app.handle()) {
                boot_diag::trace(&format!("setup: logger::init failed: {e:?}, ignore"));
            }

            boot_diag::trace("setup() done OK");
            Ok(())
        })
        .run(generate_context())
        .unwrap_or_else(|e| {
            let msg = format!("run(): tauri Builder.run() FAILED: {e:?}");
            boot_diag::trace(&msg);
            eprintln!("{msg}");
        });
    boot_diag::trace("run(): returned from Builder.run() (this is unusual on mobile)");
}
