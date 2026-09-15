package com.triangle.app.data

/**
 * Ported from script.js's HABIT_COLORS / HABIT_ICONS (assets/script.js
 * ~8445-8630) — the same color swatches and icon SVGs the WebView's task
 * and habit color/icon pickers use, and the same shape a task/habit's
 * `iconColor`/`iconSvg` fields are stored as in Firebase (a hex string and
 * a full `<svg>...</svg>` markup string, respectively — NOT an icon key).
 *
 * ICONS below is a curated ~58-icon subset of the WebView's ~110, used for
 * the native CREATE/EDIT icon picker grid — a deliberate scope trim (see
 * the Milestone 2 plan's "HABIT_COLORS / HABIT_ICONS palettes" note).
 * READING/rendering an icon is NOT limited to this subset, though: any
 * task/habit's stored `iconSvg` — including ones picked via the WebView
 * using an icon outside this subset — renders with full fidelity via
 * SvgPathIcon (com.triangle.app.ui.SvgPathIcon), which parses whatever SVG
 * path data is actually stored, not a lookup against this list.
 */
object HabitPalette {

    // Same 129 hex values as script.js's HABIT_COLORS, same order (grouped
    // by hue family — reds, oranges, ambers, ... grays).
    val COLORS: List<String> = listOf(
        "#F44336", "#D32F2F", "#B71C1C", "#E57373", "#EF5350", "#FF6659", "#C62828", "#E53935", "#FF8A80", "#FFCDD2",
        "#FF9800", "#F57C00", "#E65100", "#FFB74D", "#FFA726", "#FF6D00", "#FFCC80", "#FF7043", "#D84315", "#FFAB40",
        "#FFC107", "#FFB300", "#FF8F00", "#FFD54F", "#FFE082", "#FFA000", "#C9930A", "#FFCA28", "#FFECB3", "#D4A03C",
        "#FFEB3B", "#FDD835", "#F9A825", "#FFF176", "#FFF9C4", "#FBC02D", "#FFEE58", "#F57F17", "#FFFDE7", "#E6D200",
        "#8BC34A", "#7CB342", "#558B2F", "#AED581", "#9CCC65",
        "#4CAF50", "#388E3C", "#1B5E20", "#66BB6A", "#81C784", "#2E7D32", "#43A047", "#A5D6A7", "#00A876", "#2D7A3A",
        "#009688", "#00796B", "#004D40", "#4DB6AC", "#80CBC4", "#00897B", "#26A69A", "#B2DFDB", "#0D9488", "#00695C",
        "#00BCD4", "#0097A7", "#006064", "#4DD0E1", "#B2EBF2", "#00ACC1", "#26C6DA", "#80DEEA", "#1EC2D4", "#0A7A7A",
        "#2196F3", "#1976D2", "#0D47A1", "#64B5F6", "#BBDEFB", "#1E88E5", "#42A5F5", "#90CAF9", "#1565C0", "#1E90E0",
        "#3F51B5", "#303F9F", "#1A237E", "#7986CB", "#C5CAE9", "#3D52C7", "#5C6BC0", "#7484C7",
        "#9C27B0", "#7B1FA2", "#4A148C", "#BA68C8", "#E1BEE7", "#8E24AA", "#AB47BC", "#6A1B9A", "#D1C4E9",
        "#E91E63", "#C2185B", "#880E4F", "#F06292", "#F8BBD0", "#D81B60", "#EC407A", "#FCE4EC", "#D4145A", "#E0508A",
        "#795548", "#5D4037", "#3E2723", "#A1887F", "#D7CCC8", "#6D4C41", "#8D6E63", "#BCAAA4", "#7A5A47", "#9C8477",
        "#EEEEEE", "#E0E0E0", "#BDBDBD", "#9E9E9E", "#757575", "#616161", "#424242"
    )

    val DEFAULT_COLOR = "#009688" // teal — a reasonable neutral default

    // Curated subset of HABIT_ICONS, verbatim SVG source (same paths/viewBox
    // as script.js — fill="currentColor" so tint is applied at render time).
    val ICONS: Map<String, String> = linkedMapOf(
        "check" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>""",
        "star" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>""",
        "heart" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>""",
        "lightning" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M7 2v11h3v9l7-12h-4l4-8z"/></svg>""",
        "target" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z"/><path d="M12 6c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm0 10c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4z"/><circle cx="12" cy="12" r="2"/></svg>""",
        "flag" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6z"/></svg>""",
        "bell" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/></svg>""",
        "idea" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z"/></svg>""",
        "award" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94.63 1.5 1.98 2.63 3.61 2.96V19H7v2h10v-2h-4v-3.1c1.63-.33 2.98-1.46 3.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z"/></svg>""",
        "send" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>""",
        "puzzle" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7s2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5S21.88 11 20.5 11z"/></svg>""",
        "inbox" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M19 3H4.99c-1.11 0-1.98.89-1.98 2L3 19c0 1.1.88 2 1.99 2H19c1.1 0 2-.9 2-2V5c0-1.11-.9-2-2-2zm0 12h-4c0 1.66-1.35 3-3 3s-3-1.34-3-3H4.99V5H19v10z"/></svg>""",
        "book" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z"/></svg>""",
        "laptop" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M20 18c1.1 0 1.99-.9 1.99-2L22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2H0v2h24v-2h-4zM4 6h16v10H4V6z"/></svg>""",
        "code" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z"/></svg>""",
        "office" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M20 6h-2.18c.07-.44.18-.87.18-1.3C18 2.57 15.43 0 12.3 0c-1.7 0-3.21.77-4.27 1.99L12 6H20zm-7.5 0l-2.38-2.78C10.63 2.46 11.44 2 12.3 2c1.67 0 3.13.97 3.68 2.42L15 6h-2.5zM4 6c-1.1 0-2 .9-2 2v11c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2H4zm8 11c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3z"/></svg>""",
        "pencil" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>""",
        "chart" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z"/></svg>""",
        "clipboard" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M19 2h-4.18C14.4.84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm2 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V6h10v2z"/></svg>""",
        "map" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M20.5 3l-.16.03L15 5.1 9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5zM15 19l-6-2.11V5l6 2.11V19z"/></svg>""",
        "globe" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>""",
        "tools" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z"/></svg>""",
        "camera" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="3.2"/><path d="M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z"/></svg>""",
        "brush" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M7 14c-1.66 0-3 1.34-3 3 0 1.31-1.16 2-2 2 .92 1.22 2.49 2 4 2 2.21 0 4-1.79 4-4 0-1.66-1.34-3-3-3zm13.71-9.37l-1.34-1.34c-.39-.39-1.02-.39-1.41 0L9 12.25 11.75 15l8.96-8.96c.39-.39.39-1.02 0-1.41z"/></svg>""",
        "music" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/></svg>""",
        "sun" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91l-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z"/></svg>""",
        "moon" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z"/></svg>""",
        "timer" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"/></svg>""",
        "water" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2c-5.33 4.55-8 8.48-8 11.8 0 4.98 3.8 8.2 8 8.2s8-3.22 8-8.2c0-3.32-2.67-7.25-8-11.8z"/></svg>""",
        "coffee" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M20 3H4v10c0 2.21 1.79 4 4 4h6c2.21 0 4-1.79 4-4v-3h2c1.11 0 2-.89 2-2V5c0-1.11-.89-2-2-2zm0 5h-2V5h2v3zM4 19h16v2H4z"/></svg>""",
        "cutlery" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M18.06 22.99h1.66c.84 0 1.53-.64 1.63-1.46L23 5.05h-5V1h-1.97v4.05h-4.97l.3 2.34c1.71.47 3.31 1.32 4.27 2.26 1.44 1.42 2.43 2.89 2.43 5.29v8.05zM1 21.99V21h15.03v.99c0 .55-.45 1-1.01 1H2.01c-.56 0-1.01-.45-1.01-1zm15.03-7c0-3.5-3.47-6.25-7.52-6.25S1 11.49 1 14.99h15.03z"/></svg>""",
        "meditation" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/></svg>""",
        "home" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/></svg>""",
        "flower" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 22q-1.25 0-2.125-.875T9 19q0-.575.2-1.1t.6-.9q-.8-.35-1.4-.95T7.45 14.6q-.95.35-1.975.375T3.5 14.7q-.875-.5-1.188-1.413T2.55 11.4q.525-.825 1.388-1.212T5.9 10q-.35-.8-.35-1.625T5.9 6.75q.525-.825 1.4-1.15t1.8.025q.35-.95.975-1.688T11.5 2.8q1 .125 1.65.838T13.8 5.4q.925-.35 1.875-.3t1.675.55q.875.5 1.05 1.425t-.225 1.775Q19.2 9.2 19.55 10t.45 1.725q0 1-.313 1.9t-.887 1.55q.775.375 1.238 1.063T20.5 17.65q0 1.025-.538 1.863T18.5 20.8q-.925.225-1.838-.1T15.2 19.55q-.375.8-1.062 1.625T12 22z"/></svg>""",
        "paw" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M4.5 11c1.1 0 2-.9 2-2S5.6 7 4.5 7 2.5 7.9 2.5 9s.9 2 2 2zm3-4C8.6 7 9.5 6.1 9.5 5S8.6 3 7.5 3 5.5 3.9 5.5 5s.9 2 2 2zm9 0c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm3 4c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm-1.5 1c-.3 0-5.6 3.8-6 4-.4.1-5.7-4-6-4C5 12 3 14 3 16c0 2.8 2.7 5 6 5 .6 0 1.2-.1 1.8-.3.4-.1.8-.2 1.2-.2s.8.1 1.2.2c.6.2 1.2.3 1.8.3 3.3 0 6-2.2 6-5 0-2-2-3-2-3z"/></svg>""",
        "bicycle" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M15.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM5 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm5.8-10l2.4-2.4.8.8c1.3 1.3 3 2.1 5.1 2.1V9c-1.5 0-2.7-.6-3.6-1.5l-1.9-1.9c-.5-.4-1-.6-1.6-.6s-1.1.2-1.4.6L7.8 8.4c-.4.4-.6.9-.6 1.4 0 .6.2 1.1.6 1.4L11 14v5h2v-6l-2.2-2.5zM19 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5z"/></svg>""",
        "bug" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 2.17C12.96 5.06 12.49 5 12 5s-.96.06-1.41.17L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-6 8h-4v-2h4v2zm0-4h-4v-2h4v2z"/></svg>""",
        "basketball" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4.07 12.28c1.65-.17 3.1-.93 4.16-2.07L9.66 11.7c-1.18 1.28-2.83 2.1-4.59 2.27v-1.69zm16-.56c-1.65.16-3.1.91-4.17 2.04l-1.63-1.63c1.05-1.06 2.44-1.77 4.01-1.92v1.88c0 .01.44-.01-.21.03zm-8-8c1.86 0 3.53.8 4.76 2.04L14.3 8.3c-.73-1.41-2.1-2.3-3.6-2.3v-2c.44 0 .88.05 1.3.15l-.01-.15zm0 16c-1.86 0-3.53-.8-4.76-2.04l2.53-2.53c.73 1.41 2.1 2.3 3.6 2.3v2c-.44 0-.88-.05-1.3-.15l.01.15-.08.22z"/></svg>""",
        "soccer" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10s10-4.48 10-10S17.52 2 12 2zm1 3.3l1.35-.95a8.01 8.01 0 0 1 4.38 3.34l-.39 1.34l-1.35.46L13 6.7V5.3zm-3.35-.95L11 5.3v1.4L7.01 9.49l-1.35-.46l-.39-1.34a8.103 8.103 0 0 1 4.38-3.34zM7.08 17.11l-1.14.1A7.938 7.938 0 0 1 4 12c0-.12.01-.23.02-.35l1-.73l1.38.48l1.46 4.34l-.78 1.37zm7.42 2.48c-.79.26-1.63.41-2.5.41s-1.71-.15-2.5-.41l-.69-1.49l.64-1.1h5.11l.64 1.11l-.7 1.48zM14.27 15H9.73l-1.35-4.02L12 8.44l3.63 2.54L14.27 15zm3.79 2.21l-1.14-.1l-.79-1.37l1.46-4.34l1.39-.47l1 .73c.01.11.02.22.02.34c0 1.99-.73 3.81-1.94 5.21z"/></svg>""",
        "tennis" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M19.52 2.49C17.18.15 12.9.62 9.97 3.55c-1.6 1.6-2.52 3.87-2.54 5.46c-.02 1.58.26 3.89-1.35 5.5l-4.24 4.24l1.42 1.42l4.24-4.24c1.61-1.61 3.92-1.33 5.5-1.35s3.86-.94 5.46-2.54c2.92-2.93 3.4-7.21 1.06-9.55zm-9.2 9.19c-1.53-1.53-1.05-4.61 1.06-6.72s5.18-2.59 6.72-1.06c1.53 1.53 1.05 4.61-1.06 6.72s-5.18 2.59-6.72 1.06z"/></svg>""",
        "cricket" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M15.05 12.81L6.56 4.32a.996.996 0 0 0-1.41 0L2.32 7.15a.996.996 0 0 0 0 1.41l8.49 8.49c.39.39 1.02.39 1.41 0l2.83-2.83a.996.996 0 0 0 0-1.41zm-.709 4.946l1.414-1.414l4.243 4.243l-1.414 1.414z"/><circle cx="18.5" cy="5.5" r="3.5"/></svg>""",
        "gymnastics" to """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M4 6c0-1.1.9-2 2-2s2 .9 2 2s-.9 2-2 2s-2-.9-2-2zM1 9h6l7-5l1.31 1.52l-4.17 2.98H14L21.8 4L23 5.4L14.5 12L14 22h-2l-.5-10L8 11H1V9z"/></svg>"""
    )

    val DEFAULT_ICON_KEY = "check"
}
