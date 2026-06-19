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
        // Android logcat 会捕获 stdout/stderr（标签通常为 Rust 的 tag 或 app tag）。
        // 用多行 + 明显前缀，方便在 logcat 里 grep。
        eprintln!("\n==== PANIC ====\nlocation: {location}\npayload: {payload}\nbacktrace below\n================\n");
        default_hook(info);
    }));
}

// TODO: 添加Panic Doc
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    install_panic_hook();
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

    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(builder.invoke_handler())
        .setup(move |app| {
            builder.mount_events(app);

            // 安卓上 app_data_dir() 偶尔会失败(权限/路径)，一旦失败原来的 `?` 会让
            // 整个 setup 返回 Err，应用直接白屏/不渲染。这里改成容错：
            // 拿不到就用 cache_dir/临时目录兜底，绝不中断 setup。
            let app_data_dir = app.path().app_data_dir().unwrap_or_else(|e| {
                eprintln!("[setup] app_data_dir() 失败: {e:?}，尝试用 cache_dir 兜底");
                let fallback = app
                    .path()
                    .cache_dir()
                    .or_else(|_| app.path().temp_dir())
                    .unwrap_or_else(|e2| {
                        eprintln!("[setup] cache_dir/temp_dir 也失败: {e2:?}，用 /tmp 兜底");
                        std::path::PathBuf::from("/tmp/jmcomic-downloader")
                    });
                eprintln!("[setup] 使用兜底 app_data_dir: {}", fallback.display());
                fallback
            });

            if let Err(e) = std::fs::create_dir_all(&app_data_dir) {
                eprintln!("[setup] 创建 app_data_dir 失败: {e:?}，继续(可能后续读写失败)");
            }

            // Config::new 失败也用默认配置兜底，不让 setup 中断
            let config = match Config::new(app.handle()) {
                Ok(c) => RwLock::new(c),
                Err(e) => {
                    eprintln!("[setup] Config::new 失败: {e:?}，用默认配置兜底");
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
                eprintln!("[setup] logger::init 失败: {e:?}，忽略继续");
            }

            Ok(())
        })
        .run(generate_context())
        .expect("error while running tauri application");
}
