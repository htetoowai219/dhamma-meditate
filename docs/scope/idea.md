# Idea: Guided Tutorials

**Date:** 2026-11-26

## One-Liner
An interactive, step-by-step guided tutorial feature in DhammaMeditate that walks unfamiliar users through key tasks directly on the live app screens.

## Problem / Purpose
New or unfamiliar users may not know where to tap to get started with DhammaMeditate. Instead of leaving them to figure it out alone or read static instructions, an in-app guided walkthrough highlights each step in place so they can learn by doing—both on first use and whenever they want a refresher later.

## Core Must-Haves
- Automatically offer the guided tutorial to first-time users, with an option to skip it.
- Provide a small on-demand button so users can open the tutorial list and replay any walkthrough later.
- Display a tutorial topic menu/list where users pick which guided walkthrough to start.
- Build an extensible tutorial foundation so additional walkthroughs can be added later without rebuilding the system.
- Show interactive spotlight overlays over live app screens: dim the background, highlight the target UI element, and display a tooltip with step progress (e.g., `Step 1 / N`), short guidance, and a **Skip** button.
- Advance step by step across screens as the user taps the highlighted control, finishing with a final completion/confirmation step (e.g., "Got it").
- Include two initial tutorials:
  - **How to add an audio**
  - **How to create a session**

## Nice-to-Haves / Later
- Additional guided tutorials beyond the initial two.
