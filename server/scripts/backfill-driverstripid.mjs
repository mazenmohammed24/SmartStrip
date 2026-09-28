#!/usr/bin/env node
/**
 * Backfill driverStripId in events and notifications
 * Maps stripId to driverStripId (MAC address) for rename resilience
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.join(__dirname, '../data');

console.log('🔄 Backfilling driverStripId...\n');

// Read the main database to get strip mappings
const mainDbPath = path.join(dataDir, 'main.json');
const mainDb = JSON.parse(fs.readFileSync(mainDbPath, 'utf8'));

// Create a map of stripId -> driverStripId
const stripIdToDriverIdMap = new Map();
mainDb.strips.forEach((strip) => {
    stripIdToDriverIdMap.set(strip.id, strip.driverStripId);
    console.log(`✓ Mapped ${strip.id} -> ${strip.driverStripId}`);
});

console.log(`\nFound ${stripIdToDriverIdMap.size} strips\n`);

// Backfill events.json
const eventsDbPath = path.join(dataDir, 'events.json');
const eventsDb = JSON.parse(fs.readFileSync(eventsDbPath, 'utf8'));

let backfilledEvents = 0;
eventsDb.events.forEach((event) => {
    if (!event.driverStripId && event.stripId) {
        const driverStripId = stripIdToDriverIdMap.get(event.stripId);
        if (driverStripId) {
            event.driverStripId = driverStripId;
            backfilledEvents++;
        }
    }
});

if (backfilledEvents > 0) {
    fs.writeFileSync(eventsDbPath, JSON.stringify(eventsDb, null, 2), 'utf8');
    console.log(`✅ Backfilled ${backfilledEvents} events with driverStripId\n`);
} else {
    console.log(`✓ No events needed backfill (already have driverStripId)\n`);
}

// Backfill notifications.json
let backfilledNotifications = 0;
mainDb.notifications.forEach((notification) => {
    if (!notification.relatedStripMac && notification.relatedStripId) {
        const driverStripId = stripIdToDriverIdMap.get(notification.relatedStripId);
        if (driverStripId) {
            notification.relatedStripMac = driverStripId;
            backfilledNotifications++;
        }
    }
});

if (backfilledNotifications > 0) {
    fs.writeFileSync(mainDbPath, JSON.stringify(mainDb, null, 2), 'utf8');
    console.log(`✅ Backfilled ${backfilledNotifications} notifications with relatedStripMac\n`);
} else {
    console.log(`✓ No notifications needed backfill (already have relatedStripMac)\n`);
}

console.log('✨ Backfill complete!');
console.log(`Summary: ${backfilledEvents} events + ${backfilledNotifications} notifications updated`);
