use crate::config::Site;
use crate::http_client::MimicClient;
use scraper::{Html, Selector};
use serde::{Deserialize, Serialize};
use anyhow::{Result, anyhow};
use tracing::{info, warn};
use regex::Regex;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Availability {
    pub date: String,
    pub booking_url: String,
}

pub struct ScraperEngine<'a> {
    pub client: &'a MimicClient,
    pub site: &'a Site,
}

impl<'a> ScraperEngine<'a> {
    pub fn new(client: &'a MimicClient, site: &'a Site) -> Self {
        Self { client, site }
    }

    /// Fetches and parses availability for a specific date string (YYYY-MM-DD).
    /// Corresponds to Go's FetchForDateWithBody.
    pub async fn fetch_availability(&self, target_date: &str, museum_id: &str) -> Result<Vec<Availability>> {
        let url = format!(
            "{}{}?museum={}&date={}&digital={}&physical={}&location={}",
            self.site.baseurl,
            self.site.availabilityendpoint,
            museum_id,
            target_date,
            self.site.digital,
            self.site.physical,
            self.site.location
        );

        info!("Scraper: Checking Availability (AJAX) -> {}", target_date);

        // Mimic the AJAX request exactly as the browser/Go agent does
        let mut request = self.client.inner.get(&url)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "text/html, */*; q=0.01");

        // Set referer based on museum slug if available
        if !self.site.preferredslug.is_empty() {
            let referer = format!("{}/passes/{}", self.site.baseurl, self.site.preferredslug);
            request = request.header("Referer", referer);
        }

        let response = request.send().await?;
        
        if !response.status().is_success() {
            return Err(anyhow!("Unexpected status code: {}", response.status()));
        }

        let html_body = response.text().await?;
        let availabilities = self.parse_html(&html_body);

        Ok(availabilities)
    }

    /// Internal parser logic using CSS selectors and Regex fallback.
    /// Corresponds to Go's parseFromDoc.
    fn parse_html(&self, html: &str) -> Vec<Availability> {
        let document = Html::parse_document(html);
        let mut results = Vec::new();

        // 1. Primary Selector (Digital & Available)
        if let Ok(selector) = Selector::parse("a.s-lc-pass-availability.s-lc-pass-digital.s-lc-pass-available") {
            for element in document.select(&selector) {
                if let (Some(href), Some(text)) = (element.value().attr("href"), Some(element.text().collect::<String>())) {
                    results.push(Availability {
                        date: text.trim().to_string(),
                        booking_url: self.ensure_absolute_url(href),
                    });
                }
            }
        }

        // 2. Secondary Selector (Any Available)
        if results.is_empty() {
            if let Ok(selector) = Selector::parse("a.s-lc-pass-availability.s-lc-pass-available") {
                for element in document.select(&selector) {
                    if let (Some(href), Some(text)) = (element.value().attr("href"), Some(element.text().collect::<String>())) {
                        results.push(Availability {
                            date: text.trim().to_string(),
                            booking_url: self.ensure_absolute_url(href),
                        });
                    }
                }
            }
        }

        // 3. Regex Fallback (Matches Go's implementation for resilience)
        if results.is_empty() {
            let re = Regex::new(r#"(?i)<a\s+[^>]*href="([^"]*/book[^"]*)"[^>]*>(\d+)</a>"#).unwrap();
            for cap in re.captures_iter(html) {
                results.push(Availability {
                    date: cap[2].to_string(),
                    booking_url: self.ensure_absolute_url(&cap[1]),
                });
            }
        }

        if results.is_empty() {
            warn!("No availabilities found in HTML response");
        } else {
            info!("Parsed {} availabilities", results.len());
        }

        results
    }

    fn ensure_absolute_url(&self, raw_url: &str) -> String {
        if raw_url.starts_with("http") {
            raw_url.to_string()
        } else {
            format!("{}/{}", self.site.baseurl.trim_end_matches('/'), raw_url.trim_start_matches('/'))
        }
    }
}
