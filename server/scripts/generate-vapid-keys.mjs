#!/usr/bin/env node
import webpush from 'web-push';

const keys = webpush.generateVAPIDKeys();
console.log('\nAdd these to server/.env:\n');
console.log(`VAPID_PUBLIC_KEY=${keys.publicKey}`);
console.log(`VAPID_PRIVATE_KEY=${keys.privateKey}`);
console.log('\n(Keep the private key secret - do not commit .env.)\n');
