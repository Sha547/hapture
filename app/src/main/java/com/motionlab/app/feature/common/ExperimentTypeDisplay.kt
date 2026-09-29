package com.motionlab.app.feature.common

import com.motionlab.app.data.ExperimentType
import com.motionlab.app.ui.design.IconKind

/** How an [ExperimentType] shows up anywhere it's listed: Home, New Experiment, a Timeline's steps. */
val ExperimentType.label: String
    get() = when (this) {
        ExperimentType.SPRING_DRAG -> "Spring drag"
        ExperimentType.MAGNETIC_SNAP -> "Magnetic snap"
        ExperimentType.SWIPE_FLING -> "Swipe and fling"
        ExperimentType.BOTTOM_SHEET -> "Bottom sheet"
        ExperimentType.PULL_REFRESH -> "Pull to refresh"
        ExperimentType.PINCH_ZOOM -> "Pinch to zoom"
        ExperimentType.DRAG_REORDER -> "Drag to reorder"
        ExperimentType.TOGGLE -> "Toggle"
        ExperimentType.BUTTON_PRESS -> "Button press"
        ExperimentType.TAB_INDICATOR -> "Tab indicator"
        ExperimentType.STAGGER_LIST -> "Staggered list"
        ExperimentType.LIKE_BURST -> "Like burst"
        ExperimentType.PREDICTIVE_BACK -> "Predictive back"
    }

val ExperimentType.icon: IconKind
    get() = when (this) {
        ExperimentType.SPRING_DRAG -> IconKind.SPRING
        ExperimentType.MAGNETIC_SNAP -> IconKind.MAGNET
        ExperimentType.SWIPE_FLING -> IconKind.SWIPE
        ExperimentType.BOTTOM_SHEET -> IconKind.SHEET
        ExperimentType.PULL_REFRESH -> IconKind.REFRESH
        ExperimentType.PINCH_ZOOM -> IconKind.ZOOM
        ExperimentType.DRAG_REORDER -> IconKind.REORDER
        ExperimentType.TOGGLE -> IconKind.TOGGLE
        ExperimentType.BUTTON_PRESS -> IconKind.TAP
        ExperimentType.TAB_INDICATOR -> IconKind.TABS
        ExperimentType.STAGGER_LIST -> IconKind.STAGGER
        ExperimentType.LIKE_BURST -> IconKind.HEART
        ExperimentType.PREDICTIVE_BACK -> IconKind.BACK
    }
