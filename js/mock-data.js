/**
 * The only source of sample dining data. All times are illustrative.
 * A future API adapter can return this same snapshot shape.
 */
const locations = [
  {
    id: 'dining-hall-a',
    name: 'The Marketplace',
    subtitle: 'Cornell Campus Center',
    occupancy: 42,
    capacity: 495,
    waitMinutes: 3,
    description: 'A welcoming spot for a sit-down lunch, with rotating entrées, fresh salads, and everyday favorites.',
    hours: {
      0: [{ label: 'Brunch', open: '09:00', close: '14:00' }, { label: 'Dinner', open: '17:30', close: '20:30' }],
      1: [{ label: 'Breakfast', open: '07:15', close: '10:00' }, { label: 'Lunch', open: '11:30', close: '14:00' }, { label: 'Dinner', open: '17:30', close: '21:30' }],
      2: [{ label: 'Breakfast', open: '07:15', close: '10:00' }, { label: 'Lunch', open: '11:30', close: '14:00' }, { label: 'Dinner', open: '17:30', close: '21:30' }],
      3: [{ label: 'Breakfast', open: '07:15', close: '10:00' }, { label: 'Lunch', open: '11:30', close: '14:00' }, { label: 'Dinner', open: '17:30', close: '21:30' }],
      4: [{ label: 'Breakfast', open: '07:15', close: '10:00' }, { label: 'Lunch', open: '11:30', close: '14:00' }, { label: 'Dinner', open: '17:30', close: '21:30' }],
      5: [{ label: 'Breakfast', open: '07:15', close: '10:00' }, { label: 'Lunch', open: '11:30', close: '14:00' }, { label: 'Dinner', open: '17:30', close: '19:30' }],
      6: [{ label: 'Brunch', open: '09:00', close: '14:00' }, { label: 'Dinner', open: '17:30', close: '19:30' }],
    },
    scheduleGroups: [
      { days: 'Monday–Thursday', meals: ['Breakfast: 7:15 AM–10:00 AM', 'Lunch: 11:30 AM–2:00 PM', 'Dinner: 5:30 PM–9:30 PM'] },
      { days: 'Friday', meals: ['Breakfast: 7:15 AM–10:00 AM', 'Lunch: 11:30 AM–2:00 PM', 'Dinner: 5:30 PM–7:30 PM'] },
      { days: 'Saturday–Sunday', meals: ['Brunch: 9:00 AM–2:00 PM'] },
      { days: 'Saturday', meals: ['Dinner: 5:30 PM–7:30 PM'] },
      { days: 'Sunday', meals: ['Dinner: 5:30 PM–8:30 PM'] },
    ],
  },
  {
    id: 'dining-hall-b',
    name: 'Tarpotle',
    subtitle: 'East End',
    occupancy: 98,
    capacity: 100,
    waitMinutes: 8,
      description: 'A casual gathering place with made-to-order bowls, sandwiches, and space to catch up between classes.',
      hours: {
        0: [],
        1: [{ label: 'Open', open: '07:30', close: '21:00' }],
        2: [{ label: 'Open', open: '07:30', close: '21:00' }],
        3: [{ label: 'Open', open: '07:30', close: '21:00' }],
        4: [{ label: 'Open', open: '07:30', close: '21:00' }],
        5: [{ label: 'Open', open: '07:30', close: '21:00' }],
        6: [],
      },
      scheduleGroups: [
        { days: 'Monday–Friday', meals: ['7:30 AM–9:00 PM'] },
        { days: 'Saturday–Sunday', meals: ['Closed'] },
      ],
  },
];

const mockSnapshot = {
  snapshotLabel: '12:30 PM sample',
  locations,
};

export function getDiningSnapshot() {
  return structuredClone(mockSnapshot);
}
