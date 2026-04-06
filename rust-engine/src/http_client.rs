use wreq::Client;
use wreq_util::Emulation;
use anyhow::Result;
use std::time::Duration;
use rand::seq::SliceRandom;

/// MimicClient wraps the wreq client with specialized impersonation profiles.
/// It ensures that every request sent from the Android device carries a
/// network-level signature that is indistinguishable from a real browser.
pub struct MimicClient {
    pub inner: Client,
    pub profile_name: String,
}

impl MimicClient {
    /// Creates a new client instance with a randomized high-fidelity profile.
    pub fn new(timeout_ms: u64) -> Result<Self> {
        // Selection of modern browser profiles supported in wreq 6.x
        // These profiles use BoringSSL-based TLS fingerprinting
        let profiles = vec![
            (Emulation::Chrome123, "Chrome 123"),
            (Emulation::Safari17_2_1, "Safari 17.2.1"),
            (Emulation::Edge122, "Edge 122"),
            (Emulation::Chrome120, "Chrome 120"),
            (Emulation::Firefox128, "Firefox 128"),
            (Emulation::Chrome136, "Chrome 136"),
            (Emulation::Safari18, "Safari 18"),
        ];

        let mut rng = rand::thread_rng();
        let (emulation, name) = profiles.choose(&mut rng).unwrap();

        // Build the client with the chosen impersonation identity.
        // wreq uses .emulation() instead of .impersonate() from old rquest API
        let client = Client::builder()
            .emulation(*emulation)
            .timeout(Duration::from_millis(timeout_ms))
            .cookie_store(true)
            .gzip(true)
            .brotli(true)
            .build()?;

        Ok(Self {
            inner: client,
            profile_name: name.to_string(),
        })
    }

    /// Creates a new client with a specific emulation profile
    pub fn with_profile(timeout_ms: u64, emulation: Emulation, name: &str) -> Result<Self> {
        let client = Client::builder()
            .emulation(emulation)
            .timeout(Duration::from_millis(timeout_ms))
            .cookie_store(true)
            .gzip(true)
            .brotli(true)
            .build()?;

        Ok(Self {
            inner: client,
            profile_name: name.to_string(),
        })
    }
}
