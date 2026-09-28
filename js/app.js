import { getDiningSnapshot } from './mock-data.js';
import { getFullness } from './occupancy.js';

const grid = document.querySelector('#dining-grid');
const template = document.querySelector('#location-template');
const CAMPUS_TIME_ZONE = 'America/New_York';

function getCampusClock(date = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: CAMPUS_TIME_ZONE,
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  const value = type => parts.find(part => part.type === type)?.value;
  const day = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(value('weekday'));
  return { day, minutes: Number(value('hour')) * 60 + Number(value('minute')) };
}

function isLocationOpen(hours, date = new Date()) {
  const { day, minutes } = getCampusClock(date);
  return (hours[day] || []).some(period => {
    const toMinutes = time => time.split(':').reduce((hour, minute) => Number(hour) * 60 + Number(minute));
    return minutes >= toMinutes(period.open) && minutes < toMinutes(period.close);
  });
}

function renderSchedule(container, groups) {
  const rows = groups.map(group => {
    const row = document.createElement('div');
    row.className = 'schedule-group';
    const heading = document.createElement('h4');
    heading.textContent = group.days;
    const meals = document.createElement('ul');
    for (const meal of group.meals) {
      const item = document.createElement('li');
      item.textContent = meal;
      meals.append(item);
    }
    row.append(heading, meals);
    return row;
  });
  container.replaceChildren(...rows);
}

function createLocationCard(location, index) {
  const card = template.content.firstElementChild.cloneNode(true);
  const { tier, label, percent } = getFullness(location.occupancy, location.capacity);
  const headingId = `${location.id}-title`;
  const open = isLocationOpen(location.hours);
  const setText = (selector, value) => { card.querySelector(selector).textContent = value; };

  card.dataset.tier = tier;
  card.style.setProperty('--card-delay', `${100 + index * 90}ms`);
  card.style.setProperty('--fill-delay', `${260 + index * 90}ms`);
  card.setAttribute('aria-labelledby', headingId);
  card.querySelector('.location-name').id = headingId;
  setText('.location-name', location.name);
  setText('.location-subtitle', location.subtitle);
  const hoursBadge = card.querySelector('.hours-badge');
  hoursBadge.dataset.open = open;
  hoursBadge.setAttribute('aria-label', `${location.name} is currently ${open ? 'open' : 'closed'}`);
  setText('.hours-status', open ? 'Open' : 'Closed');
  setText('.occupancy-total', `${percent}% full`);
  setText('.occupancy-state', label);
  setText('.wait-time', location.waitMinutes);
  setText('.location-description', location.description);
  renderSchedule(card.querySelector('.location-hours'), location.scheduleGroups);
  setText('.details-disclaimer', location.scheduleIsSample ? 'Sample hours for this preview.' : 'Hours shown in campus local time.');

  const meter = card.querySelector('[role="meter"]');
  meter.setAttribute('aria-label', `${location.name} occupancy`);
  meter.setAttribute('aria-valuemax', '100');
  meter.setAttribute('aria-valuenow', percent);
  meter.setAttribute('aria-valuetext', `${percent}% full, ${label.toLowerCase()} occupancy`);
  card.querySelector('.occupancy-fill').style.width = `${percent}%`;
  card.querySelector('summary').setAttribute('aria-label', `Location details for ${location.name}`);
  return card;
}

// Rendering is independent of the data source. A future ~60s API poll can
// pass each returned snapshot here; this preview has no network or timer logic.
export function renderDiningSnapshot(snapshot) {
  const expanded = new Set([...grid.querySelectorAll('details[open]')].map(details => details.closest('article').getAttribute('aria-labelledby')));
  const cards = snapshot.locations.map(createLocationCard);
  for (const card of cards) {
    card.querySelector('details').open = expanded.has(card.getAttribute('aria-labelledby'));
  }
  grid.replaceChildren(...cards);
  const campusTotals = snapshot.locations.reduce(
    (totals, location) => ({
      occupancy: totals.occupancy + location.occupancy,
      capacity: totals.capacity + location.capacity,
    }),
    { occupancy: 0, capacity: 0 },
  );
  document.querySelector('#campus-status').textContent = getFullness(
    campusTotals.occupancy,
    campusTotals.capacity,
  ).label;
  document.querySelector('#location-count').textContent = `${snapshot.locations.length} dining halls`;
  document.querySelector('#snapshot-time').textContent = snapshot.snapshotLabel;
}

renderDiningSnapshot(getDiningSnapshot());
