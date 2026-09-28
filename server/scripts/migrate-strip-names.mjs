#!/usr/bin/env node
/**
 * Migration script to update old activity events and notifications
 * to use the new strip names instead of MAC addresses (driverStripId)
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.join(__dirname, '../data');

console.log('🔄 Starting strip name migration...\n');

// Read the main database to get current strip names
const mainDbPath = path.join(dataDir, 'main.json');
const mainDb = JSON.parse(fs.readFileSync(mainDbPath, 'utf8'));

// Create a map of stripId -> stripName
const stripNameMap = new Map();
mainDb.strips.forEach((strip) => {
    stripNameMap.set(strip.id, strip.name);
    console.log(`✓ Mapped ${strip.id} -> "${strip.name}"`);
});

console.log(`\nFound ${stripNameMap.size} strips\n`);

// Migrate events.json
const eventsDbPath = path.join(dataDir, 'events.json');
const eventsDb = JSON.parse(fs.readFileSync(eventsDbPath, 'utf8'));

let migratedEvents = 0;
eventsDb.events.forEach((event) => {
    const correctName = stripNameMap.get(event.stripId);
    if (correctName) {
        let updated = false;

        // Update stripName if needed
        if (event.stripName !== correctName) {
            console.log(`✓ Event ${event.id}: stripName "${event.stripName}" -> "${correctName}"`);
            event.stripName = correctName;
            updated = true;
        }

        // Update message for connectivity and fleet events (even if already migrated, to ensure message matches name)
        if (event.type === 'online' && !event.message.includes(correctName)) {
            event.message = `${correctName} is now online`;
            console.log(`✓ Event ${event.id}: message updated to "${event.message}"`);
            updated = true;
        } else if (event.type === 'offline' && !event.message.includes(correctName)) {
            event.message = `${correctName} went offline`;
            console.log(`✓ Event ${event.id}: message updated to "${event.message}"`);
            updated = true;
        } else if (event.type === 'strip-added' && !event.message.includes(correctName)) {
            event.message = `${correctName} added to fleet`;
            console.log(`✓ Event ${event.id}: message updated to "${event.message}"`);
            updated = true;
        } else if (event.type === 'strip-removed' && !event.message.includes(correctName)) {
            event.message = `${correctName} removed from fleet`;
            console.log(`✓ Event ${event.id}: message updated to "${event.message}"`);
            updated = true;
        }

        if (updated) {
            migratedEvents++;
        }
    }
});

if (migratedEvents > 0) {
    fs.writeFileSync(eventsDbPath, JSON.stringify(eventsDb, null, 2), 'utf8');
    console.log(`\n✅ Updated ${migratedEvents} events in events.json`);
} else {
    console.log(`\n✓ No event updates needed`);
}

// Migrate notifications in main.json
let migratedNotifications = 0;
mainDb.notifications.forEach((notification) => {
    if (notification.relatedStripId) {
        const correctName = stripNameMap.get(notification.relatedStripId);
        if (correctName) {
            // Extract the old name from the title to see if we need to update
            const oldTitle = notification.title;

            // Handle different notification types
            if (notification.type === 'offline') {
                notification.title = `${correctName} went offline`;
            } else if (notification.type === 'online') {
                notification.title = `${correctName} is back online`;
            } else if (notification.type === 'overload') {
                notification.title = notification.title.replace(/^.*? - /, `Overload protection triggered - ${correctName}`);
            } else if (notification.type === 'schedule-fired') {
                // Keep schedule-fired as-is since it's more complex
            }

            if (notification.title !== oldTitle) {
                console.log(`✓ Notification ${notification.id}: "${oldTitle}" -> "${notification.title}"`);
                migratedNotifications++;
            }
        }
    }
});

if (migratedNotifications > 0) {
    fs.writeFileSync(mainDbPath, JSON.stringify(mainDb, null, 2), 'utf8');
    console.log(`\n✅ Updated ${migratedNotifications} notifications in main.json`);
} else {
    console.log(`\n✓ No notification updates needed`);
}

console.log('\n✨ Migration complete!');
console.log(`Summary: ${migratedEvents} events + ${migratedNotifications} notifications updated`);
