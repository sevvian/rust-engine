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
    pub fn new(timeout_ms: u64) -> Result<Self> {
        // Selection of modern browser profiles supported in rquest 0.31.0
        let profiles = vec![
            (Impersonate::Chrome123, "Chrome 123 Windows"),
            (Impersonate::Safari17_2_1, "Safari 17.2.1 (MacOS)"),
            (Impersonate::Edge122, "Edge 122 Windows"),
            (Impersonate::Chrome120, "Chrome 120 Windows"),
        ];

        let mut rng = rand::thread_rng();
        let (impersonate_type, name) = profiles.choose(&mut rng).unwrap();

        // Build the client with the chosen impersonation identity.
        let client = Client::builder()
            .impersonate(*impersonate_type)
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
