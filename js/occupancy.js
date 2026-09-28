/** Shared display rules; classify the raw ratio so tier boundaries stay exact. */
export function getFullness(occupancy, capacity) {
  const ratio = capacity > 0 ? Math.min(1, Math.max(0, occupancy / capacity)) : 0;
  const percent = Math.floor(ratio * 100);
  if (ratio < 0.2) return { tier: 'low', label: 'Quiet', percent };
  if (ratio < 0.4) return { tier: 'low', label: 'Light', percent };
  if (ratio < 0.65) return { tier: 'moderate', label: 'Moderate', percent };
  if (ratio < 0.85) return { tier: 'full', label: 'Busy', percent };
  return { tier: 'full', label: 'Full', percent };
}
