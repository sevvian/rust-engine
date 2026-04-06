fn main() {
    // Generate the UniFFI bindings from the interface definition file
    // The .udl file defines the functions and data structures shared with Kotlin
    uniffi::generate_scaffolding("src/agent.udl").expect("Failed to generate UniFFI scaffolding");
}
