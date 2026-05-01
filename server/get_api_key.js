const fs = require('fs');
const path = require('path');
const { MongoClient } = require('mongodb');

const envPath = path.join(__dirname, '.env');
if (!process.env.MONGODB_URI && fs.existsSync(envPath)) {
    const envFile = fs.readFileSync(envPath, 'utf8');
    envFile.split(/\r?\n/).forEach(line => {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) return;
        const [key, ...valueParts] = trimmed.split('=');
        const value = valueParts.join('=').trim();
        if (key && value && !process.env[key]) {
            process.env[key] = value;
        }
    });
}

let uri = process.env.MONGODB_URI;

const username = process.env.MONGODB_USERNAME;
const password = process.env.MONGODB_PASSWORD;
const host = process.env.MONGODB_HOST;
const database = process.env.MONGODB_DATABASE || 'caall_db';
const options = process.env.MONGODB_OPTIONS || 'retryWrites=true&w=majority';

if (!uri && username && password && host) {
    uri = `mongodb+srv://${encodeURIComponent(username)}:${encodeURIComponent(password)}@${host}/${database}?${options}`;
    console.log('Built MongoDB URI from raw env vars using encoded credentials.');
}

if (!uri) {
    throw new Error('Missing MongoDB connection configuration. Set MONGODB_URI or MONGODB_USERNAME, MONGODB_PASSWORD, and MONGODB_HOST in .env.');
}

function buildMongoUri(rawUri) {
    if (!rawUri.startsWith('mongodb://') && !rawUri.startsWith('mongodb+srv://')) {
        return rawUri;
    }

    if (/[?&]authSource=/i.test(rawUri)) {
        return rawUri;
    }

    if (rawUri.includes('?')) {
        return `${rawUri}&authSource=admin`;
    }

    return `${rawUri}?authSource=admin`;
}

const normalizedUri = buildMongoUri(uri);
if (normalizedUri !== uri) {
    console.log('Added authSource=admin to MongoDB URI automatically.');
    uri = normalizedUri;
}

function maskUri(connectionString) {
    return connectionString.replace(/(mongodb(?:\+srv)?:\/\/[^:]+:)([^@]+)(@)/, '$1*****$3');
}

async function getApiKey() {
    // Check if API key is already in .env
    if (process.env.GATEWAY_API_KEY) {
        console.log("API Key found in .env, skipping MongoDB lookup.");
        return process.env.GATEWAY_API_KEY;
    }

    console.log("Fetching API key from MongoDB...");
    const client = new MongoClient(uri, { serverSelectionTimeoutMS: 30000 });
    console.log("Connecting to MongoDB...");
    console.log("MongoDB URI:", maskUri(uri));
    console.log("Database: caall_db, Collection: call_log_key");
    try {
        await client.connect();
        
        console.log("Connected to MongoDB");

        const database = client.db("caall_db");
        const collection = database.collection("call_log_key");

        const result = await collection.findOne({});

        if (result && result.apiKey) {
            console.log("API Key found in MongoDB");
            return result.apiKey;
        } else {
            console.log("No API key found in collection call_log_key.");
            return null;
        }

    } catch (error) {
        console.error("Error fetching API key:", error.message || error);
        throw error;
    } finally {
        await client.close();
    }
}
// Simple Express server to provide the key to the app (Optional but recommended)
const express = require('express');
const app = express();
const port = 3000;

app.get('/api/get-key', async (req, res) => {
    try {
        const key = await getApiKey();
        console.log("Key fetched: " + key);
        if (key) {
            res.json({ apiKey: key });
        } else {
            res.status(404).json({ error: "Key not found" });
        }
    } catch (error) {
        res.status(500).json({ error: error.message || 'Failed to fetch API key' });
    }
});

app.listen(port, () => {
    console.log(`Key service listening at http://localhost:${port}`);
});
