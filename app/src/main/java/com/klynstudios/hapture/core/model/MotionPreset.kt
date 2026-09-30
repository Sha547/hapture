package com.klynstudios.hapture.core.model

/**
 * Ship with carefully tuned presets, parameterized rather than hardcoded
 * animations (spec §24). Each preset is just a (stiffnessT, dampingT) pair
 * in the same 0f..1f normalized space every slider already uses -- selecting
 * one sets the sliders to those values rather than triggering a separate
 * canned animation, so it's tunable afterward like anything else, and it's
 * covered by the same preview/export fidelity guarantee.
 */
enum class MotionPreset(val displayName: String, val stiffnessT: Float, val dampingT: Float) {
    GENTLE("Gentle", stiffnessT = 0.25f, dampingT = 0.75f),
    SNAPPY("Snappy", stiffnessT = 0.80f, dampingT = 0.75f),
    HEAVY("Heavy", stiffnessT = 0.15f, dampingT = 0.85f),
    BOUNCY("Bouncy", stiffnessT = 0.60f, dampingT = 0.25f),
    ELASTIC("Elastic", stiffnessT = 0.40f, dampingT = 0.15f),
    GOOEY("Gooey", stiffnessT = 0.20f, dampingT = 0.35f),
    MAGNETIC("Magnetic", stiffnessT = 0.70f, dampingT = 0.55f),
    INSTANT("Instant", stiffnessT = 1.00f, dampingT = 1.00f),
}
