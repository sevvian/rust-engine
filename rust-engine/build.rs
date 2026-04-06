fn main() {
    // In 0.24.3, we use uniffi_build to generate the scaffolding
    uniffi_build::generate_scaffolding("src/agent.udl")
        .expect("Failed to generate UniFFI scaffolding");
}
