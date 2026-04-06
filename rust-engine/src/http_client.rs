use rquest::{Client, r#impersonate::Impersonate};
use anyhow::Result;
use std::time::Duration;
use rand::seq::SliceRandom;

/// MimicClient wraps the rquest client with specialized impersonation profiles.
/// It ensures that every request sent from the Android device carries a 
/// network-level signature that is indistinguishable from a real browser.
pub struct MimicClient {
    pub inner: Client,
    pub profile_name: String,
}

impl MimicClient {
    /// Creates a new client instance with a randomized high-fidelity profile.
    /// timeout_ms: Maximum time to wait for a response.
    pub fn new(timeout_ms: u64) -> Result<Self> {
        // Selection of modern, high-reputation browser profiles.
        // These profiles configure TLS extensions, cipher suites, and HTTP/2 settings
        // to match the specific User-Agent exactly.
        let profiles = vec![
            (Impersonate::Chrome120, "Chrome 120 Windows"),
            (Impersonate::SafariIos17_2, "Safari iOS 17.2 (iPhone)"),
            (Impersonate::Edge120, "Edge 120 Windows"),
            (Impersonate::Chrome119, "Chrome 119 Windows"),
        ];

        let mut rng = rand::thread_rng();
        // Unwrap is safe here as the profiles list is non-empty.
        let (impersonate_type, name) = profiles.choose(&mut rng).unwrap();

        // Build the client with the chosen impersonation identity.
        let client = Client::builder()
            .impersonate(*impersonate_type)
            .timeout(Duration::from_millis(timeout_ms))
            // Enable cookie_store to maintain session state across requests (Login -> Book).
            .cookie_store(true)
            // Enforce standard compression support expected by modern WAFs.
            .gzip(true)
            .brotli(true)
            .build()?;

        Ok(Self {
            inner: client,
            profile_name: name.to_string(),
        })
    }
}
