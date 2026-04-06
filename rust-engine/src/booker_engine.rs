use crate::config::{Site, Credential, Museum};
use crate::http_client::MimicClient;
use crate::scraper_engine::Availability;
use scraper::{Html, Selector};
use anyhow::{Result, anyhow};
use tracing::{info, warn, error};
use std::collections::HashMap;

pub struct BookerEngine<'a> {
    pub client: &'a MimicClient,
    pub site: &'a Site,
    pub museum: &'a Museum,
    pub credential: &'a Credential,
}

impl<'a> BookerEngine<'a> {
    pub fn new(client: &'a MimicClient, site: &'a Site, museum: &'a Museum, credential: &'a Credential) -> Self {
        Self { client, site, museum, credential }
    }

    /// Executes the full booking flow: Navigation -> Login (if needed) -> Form Submission.
    pub async fn book(&self, availability: &Availability) -> Result<()> {
        info!("Starting booking flow for date: {}", availability.date);

        // 1. Initial GET request to the booking URL
        // We remove AJAX headers to mimic a real browser page navigation.
        let resp = self.client.inner.get(&availability.booking_url)
            .header("Referer", format!("{}/passes/{}", self.site.baseurl, self.museum.slug))
            .send()
            .await?;

        let final_url = resp.url().clone();
        let body_text = resp.text().await?;
        let document = Html::parse_document(&body_text);

        // 2. Detect Login Form
        let login_form_selector = Selector::parse("form[action*='form_login']").unwrap();
        if let Some(login_form) = document.select(&login_form_selector).next() {
            info!("Login required, performing library authentication");
            self.handle_login(login_form, final_url.as_str()).await?;
        } else {
            info!("No login form found – assuming active session or public access");
        }

        // 3. Re-fetch or parse the current page for the actual booking form (#s-lc-bform)
        // After a login redirect, we need the fresh document to extract CSRF/Hidden tokens.
        let current_resp = self.client.inner.get(final_url.as_str()).send().await?;
        let current_body = current_resp.text().await?;
        let current_doc = Html::parse_document(&current_body);

        let booking_form_selector = Selector::parse("form#s-lc-bform").unwrap();
        let booking_form = current_doc.select(&booking_form_selector).next()
            .ok_or_else(|| {
                if current_body.contains("exceed the monthly booking limit") {
                    anyhow!("Booking limit exceeded for this library card")
                } else if current_url_is_unavailable(final_url.as_str()) {
                    anyhow!("Spot already taken (unavailable)")
                } else {
                    error!("Booking form #s-lc-bform not found in page source");
                    anyhow!("Booking form not found")
                }
            })?;

        // 4. Extract all hidden fields and prepare POST data
        let mut form_data = HashMap::new();
        let input_selector = Selector::parse("input").unwrap();
        for input in booking_form.select(&input_selector) {
            let name = input.value().attr("name").unwrap_or("");
            let value = input.value().attr("value").unwrap_or("");
            let input_type = input.value().attr("type").unwrap_or("");

            if !name.is_empty() && (input_type == "hidden" || input_type == "text" || input_type == "email") {
                form_data.insert(name.to_string(), value.to_string());
            }
        }

        // Inject the user's email into the configured field
        let email_field = if self.site.bookingform.emailfield.is_empty() {
            "email"
        } else {
            &self.site.bookingform.emailfield
        };
        form_data.insert(email_field.to_string(), self.credential.email.clone());

        // 5. Submit the final booking form
        let action_url = self.extract_action_url(booking_form, final_url.as_str());
        info!("Submitting booking form to: {}", action_url);

        let submit_resp = self.client.inner.post(&action_url)
            .form(&form_data)
            .header("Referer", final_url.as_str())
            .send()
            .await?;

        let result_text = submit_resp.text().await?;

        // 6. Verify success based on indicators
        if result_text.contains(&self.site.successindicator) ||
           result_text.contains("The following Digital Pass reservation was made:") {
            info!("Booking SUCCESSFUL for {}", availability.date);
            Ok(())
        } else if result_text.contains("exceed the monthly booking limit") {
            Err(anyhow!("Booking limit exceeded"))
        } else if result_text.contains("unavailable") || result_text.contains("not available") {
            Err(anyhow!("Spot no longer available"))
        } else {
            warn!("Unexpected booking response: {}", truncate_text(&result_text, 500));
            Err(anyhow!("Booking failed: unknown response from server"))
        }
    }

    async fn handle_login(&self, form_element: scraper::ElementRef<'_>, current_url: &str) -> Result<()> {
        let auth_id = form_element.select(&Selector::parse("input[name='auth_id']").unwrap())
            .next().and_then(|e| e.value().attr("value")).unwrap_or("");

        let login_url_val = form_element.select(&Selector::parse("input[name='login_url']").unwrap())
            .next().and_then(|e| e.value().attr("value")).unwrap_or("");

        if auth_id.is_empty() || login_url_val.is_empty() {
            return Err(anyhow!("Could not extract auth_id or login_url for authentication"));
        }

        let mut login_data = HashMap::new();
        login_data.insert("auth_id".to_string(), auth_id.to_string());
        login_data.insert("login_url".to_string(), login_url_val.to_string());
        login_data.insert(self.site.loginform.usernamefield.clone(), self.credential.username.clone());
        login_data.insert(self.site.loginform.passwordfield.clone(), self.credential.password.clone());

        let action = self.extract_action_url(form_element, current_url);

        let resp = self.client.inner.post(&action)
            .form(&login_data)
            .header("Referer", current_url)
            .send()
            .await?;

        let body = resp.text().await?;
        if body.contains("Your credentials are not working.") {
            return Err(anyhow!("Invalid credentials: library system rejected login"));
        }

        info!("Login successful (Session established)");
        Ok(())
    }

    fn extract_action_url(&self, element: scraper::ElementRef, current_url: &str) -> String {
        let action = element.value().attr("action").unwrap_or("");
        if action.starts_with("http") {
            action.to_string()
        } else if action.starts_with('/') {
            let base = wreq::Url::parse(current_url).unwrap();
            format!("{}://{}{}", base.scheme(), base.host_str().unwrap_or(""), action)
        } else {
            format!("{}/{}", current_url.trim_end_matches('/'), action)
        }
    }
}

fn current_url_is_unavailable(url: &str) -> bool {
    url.contains("unavailable")
}

fn truncate_text(text: &str, max_len: usize) -> String {
    if text.len() <= max_len {
        text.to_string()
    } else {
        format!("{}...", &text[..max_len])
    }
}
