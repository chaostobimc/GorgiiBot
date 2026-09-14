#!/usr/bin/env node
/* ============================================================================
 * GorgiiBot – server.js
 * Minimaler statischer Server ohne Abhängigkeiten (reines Node.js).
 * Start:  npm start                (Port 8080, alle Interfaces)
 *         PORT=2345 npm start      (eigener Port, z. B. Raspberry Pi)
 *         HOST=127.0.0.1 npm start (nur lokal erreichbar)
 * ========================================================================== */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');

const ROOT = __dirname;
const PORT = parseInt(process.env.PORT || '8080', 10) || 8080;
const HOST = process.env.HOST || '0.0.0.0';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff',
  '.txt': 'text/plain; charset=utf-8'
};

function send(res, code, body, type) {
  res.writeHead(code, {
    'Content-Type': type || 'text/plain; charset=utf-8',
    // Kein Caching: immer frische Dateien (kein Cache-Ärger nach Updates)
    'Cache-Control': 'no-store',
    'Content-Length': Buffer.byteLength(body)
  });
  res.end(body);
}

function serveFile(res, file) {
  fs.readFile(file, function (err, data) {
    if (err) {
      send(res, 404, '404 – Nicht gefunden');
      return;
    }
    const type = MIME[path.extname(file).toLowerCase()] || 'application/octet-stream';
    res.writeHead(200, {
      'Content-Type': type,
      'Cache-Control': 'no-store',
      'Content-Length': data.length
    });
    res.end(data);
  });
}

const server = http.createServer(function (req, res) {
  try {
    const url = new URL(req.url || '/', 'http://localhost');
    const file = path.normalize(path.join(ROOT, decodeURIComponent(url.pathname)));
    // Pfad-Traversal verhindern: Anfrage muss innerhalb von ROOT bleiben
    if (file !== ROOT && file.indexOf(ROOT + path.sep) !== 0) {
      send(res, 403, '403 – Verboten');
      return;
    }
    fs.stat(file, function (err, st) {
      if (!err && st.isDirectory()) {
        serveFile(res, path.join(file, 'index.html'));
      } else if (!err && st.isFile()) {
        serveFile(res, file);
      } else {
        send(res, 404, '404 – Nicht gefunden');
      }
    });
  } catch (e) {
    send(res, 400, '400 – Ungültige Anfrage');
  }
});

server.on('error', function (err) {
  if (err.code === 'EADDRINUSE') {
    console.error('Port ' + PORT + ' ist belegt – anderen Port wählen, z. B.: PORT=2345 npm start');
  } else {
    console.error('Serverfehler:', err.message);
  }
  process.exit(1);
});

function lanIps() {
  const out = [];
  const ifs = os.networkInterfaces();
  Object.keys(ifs).forEach(function (name) {
    ifs[name].forEach(function (ni) {
      if (ni.family === 'IPv4' && !ni.internal) out.push(ni.address);
    });
  });
  return out;
}

server.listen(PORT, HOST, function () {
  console.log('GorgiiBot läuft:');
  console.log('  Lokal:    http://localhost:' + PORT + '/');
  lanIps().forEach(function (ip) {
    console.log('  Netzwerk: http://' + ip + ':' + PORT + '/');
  });
  console.log('Beenden mit Strg+C.');
});
