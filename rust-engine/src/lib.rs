uniffi::include_scaffolding!("agent");

use crate::config::{AppConfig, ScheduledRun};
use crate::http_client::MimicClient;
use crate::scraper_engine::ScraperEngine;
use crate::book_engine::BookerEngine; // Matches file name from previous step
use crate::scraper_engine::Availability;

use std::sync::Arc;
use tokio::sync::{RwLock, oneshot};
use tokio::time::{sleep, Duration, Instant};
use chrono::{Utc, DateTime};
use anyhow::{Result, anyhow};
use tracing::{info, warn, error};

pub mod config;
pub mod http_client;
pub mod scraper_engine;
#[path = "booker_engine.rs"]
pub mod book_engine;

// Real-time status structure exported to Kotlin
pub struct AgentInternalState {
    pub is_running: bool,
    pub last_log: String,
    pub current_run_id: String,
}

pub struct BookingAgent {
    state: Arc<RwLock<AgentInternalState>>,
    cancel_tx: Arc<RwLock<Option<oneshot::Sender<()>>>>,
}

impl Default for BookingAgent {
    fn default() -> Self {
        Self::new()
    }
}

impl BookingAgent {
    pub fn new() -> Self {
        Self {
            state: Arc::new(RwLock::new(AgentInternalState {
                is_running: false,
                last_log: "Engine Initialized".to_string(),
                current_run_id: String::new(),
            })),
            cancel_tx: Arc::new(RwLock::new(None)),
        }
    }

    /// Logs a message and updates the internal state for the UI
    async fn log(&self, msg: String) {
        info!("{}", msg);
        let mut state = self.state.write().await;
        state.last_log = msg;
    }

    /// High-precision wait loop. Spins for the last 5ms to hit exact drop time.
    async fn wait_until_precision(&self, target: DateTime<Utc>) -> Result<()> {
        let mut now = Utc::now();
        while now < target {
            let diff = target - now;
            let diff_ms = diff.num_milliseconds();

            if diff_ms > 10 {
                // Sleep for most of the duration
                sleep(Duration::from_millis((diff_ms - 5) as u64)).await;
            } else {
                // Spin-wait for the last few milliseconds for microsecond accuracy
                // This prevents thread-context-switch latency at the strike moment
                std::hint::spin_loop();
            }
            now = Utc::now();
        }
        Ok(())
    }
}

#[uniffi::export]
impl BookingAgent {
    pub async fn get_status(&self) -> EngineStatus {
        let state = self.state.read().await;
        EngineStatus {
            is_running: state.is_running,
            last_log: state.last_log.clone(),
            current_run_id: state.current_run_id.clone(),
        }
    }

    pub fn stop(&self) {
        let mut tx_lock = self.cancel_tx.blocking_write();
        if let Some(tx) = tx_lock.take() {
            let _ = tx.send(());
        }
    }

    pub async fn start_strike(&self, config_json: String, run_id: String) -> Result<bool, EngineError> {
        // 1. Setup State
        {
            let mut state = self.state.write().await;
            if state.is_running {
                return Err(EngineError::InternalError);
            }
            state.is_running = true;
            state.current_run_id = run_id.clone();
        }

        let (tx, mut rx) = oneshot::channel();
        {
            let mut tx_lock = self.cancel_tx.write().await;
            *tx_lock = Some(tx);
        }

        // 2. Parse Config
        let cfg: AppConfig = serde_json::from_str(&config_json).map_err(|_| EngineError::ConfigError)?;
        let run = cfg.scheduled_runs.iter().find(|r| r.id == run_id)
            .ok_or(EngineError::ConfigError)?.clone();

        let agent_ref = Arc::new(self);
        
        // 3. Main Execution Block
        let result = tokio::select! {
            res = self.execute_strike_logic(cfg, run) => res,
            _ = &mut rx => {
                info!("Strike cancelled by user");
                Ok(false)
            }
        };

        // 4. Cleanup State
        {
            let mut state = self.state.write().await;
            state.is_running = false;
            state.current_run_id = String::new();
        }

        match result {
            Ok(success) => Ok(success),
            Err(e) => {
                error!("Strike Error: {:?}", e);
                Err(EngineError::BookingFailed)
            }
        }
    }
}

impl BookingAgent {
    async fn execute_strike_logic(&self, cfg: AppConfig, run: ScheduledRun) -> Result<bool> {
        self.log(format!("Starting Strike: Site={}, Museum={}", run.sitekey, run.museumslug)).await;

        let site = cfg.sites.get(&run.sitekey).ok_or_else(|| anyhow!("Site not found"))?;
        let museum = site.museums.get(&run.museumslug).ok_or_else(|| anyhow!("Museum not found"))?;
        
        // Initialize the Mimicry Client (BoringSSL/JA3)
        let client = MimicClient::new(30000)?;
        self.log(format!("Identity Locked: {}", client.profile_name)).await;

        // Pre-warm Phase (30s before)
        let pre_warm_time = run.droptime - Duration::from_millis(cfg.pre_warm_offset_ms);
        let now = Utc::now();
        if pre_warm_time > now {
            self.log("Waiting for pre-warm...".to_string()).await;
            sleep(Duration::from_millis((pre_warm_time - now).num_milliseconds() as u64)).await;
        }

        // Hit the base URL to establish TLS session and cookies
        self.log("Pre-warming TLS session...".to_string()).await;
        let _ = client.inner.get(&site.baseurl).send().await;

        // Precision Strike Timing
        self.log("Waiting for exact strike moment...".to_string()).await;
        self.wait_until_precision(run.droptime).await?;
        self.log("STRIKE INITIATED".to_string()).await;

        let scraper = ScraperEngine::new(&client, site);
        let deadline = Instant::now() + Duration::from_millis(cfg.check_window_ms);
        
        // Strike Loop
        while Instant::now() < deadline {
            for month_idx in 0..cfg.months_to_check {
                let target_month = Utc::now() + Duration::from_secs(month_idx as u64 * 30 * 24 * 3600);
                let date_str = target_month.format("%Y-%m-%d").to_string();

                let avails = scraper.fetch_availability(&date_str, &museum.museumid).await?;
                
                if !avails.is_empty() {
                    if run.mode == "booking" {
                        if let Some(target) = self.find_preferred_match(&avails, &cfg.preferred_days) {
                            self.log(format!("Match found: {}. Attempting booking...", target.date)).await;
                            
                            let cred = cfg.credentials.get(&run.credentialid)
                                .ok_or_else(|| anyhow!("Credential not found"))?;
                                
                            let booker = book_engine::BookerEngine::new(&client, site, museum, cred);
                            match booker.book(&target).await {
                                Ok(_) => {
                                    self.log("BOOKING SUCCESSFUL".to_string()).await;
                                    return Ok(true);
                                },
                                Err(e) => {
                                    self.log(format!("Booking failed: {}", e)).await;
                                    return Err(e);
                                }
                            }
                        }
                    } else {
                        // Alert Mode
                        self.log(format!("Alert: Found {} slots", avails.len())).await;
                        return Ok(true);
                    }
                }
            }

            // Interval & Jitter
            let mut interval = cfg.check_interval_ms;
            if cfg.request_jitter_ms > 0 {
                interval += rand::random::<u64>() % cfg.request_jitter_ms;
            }
            sleep(Duration::from_millis(interval)).await;
        }

        self.log("Strike window expired: No availability found".to_string()).await;
        Ok(false)
    }

    fn find_preferred_match(&self, avails: &[Availability], preferred: &[String]) -> Option<Availability> {
        for av in avails {
            // Very simple check: see if the date string contains a preferred day name
            // In production, parse the date properly to check weekday
            for day in preferred {
                if av.date.contains(day) {
                    return Some(av.clone());
                }
            }
        }
        None
    }
}

// Utility function used by UDL generated code
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum EngineError {
    #[error("Configuration error")]
    ConfigError,
    #[error("Network failure")]
    NetworkError,
    #[error("Booking failed")]
    BookingFailed,
    #[error("Internal engine error")]
    InternalError,
}
