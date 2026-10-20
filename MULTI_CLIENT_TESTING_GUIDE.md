# 📺 Virtual Television Broadcast Simulator — Multi-Client Testing Guide

This guide explains how to test **multiple TV screens (3–4 windows/tabs)** on a single machine simultaneously and control each TV independently or all together using the React PWA Remote.

---

## 🏗️ How Multi-Client Routing Works

```
                                 ┌──────────────────────────────────────────────┐
                                 │     Tomcat 10 Central WebSocket Hub          │
                                 │       ws://localhost:8085/tv-simulator/ws/tv │
                                 └──────┬──────────────────┬──────────────┬─────┘
                                        │                  │              │
                    Target: Client #1   │ Target: Client #2│ BroadcastAll │
                                        ▼                  ▼              ▼
                               ┌─────────────────┐ ┌───────────────┐ ┌───────────────┐
                               │  TV Client #1   │ │ TV Client #2  │ │ TV Client #3  │
                               │  "Living Room"  │ │  "Bedroom"    │ │  "Kitchen"    │
                               │  Channel 02     │ │  Channel 05   │ │  Channel 08   │
                               └─────────────────┘ └───────────────┘ └───────────────┘
                                        ▲
                                        │ WebSocket Commands
                               ┌────────┴────────┐
                               │   PWA Remote    │
                               │  Target: TV #1  │
                               └─────────────────┘
```

1. **Auto-Registration & Unique Client IDs:**
   Every time a TV window/tab opens `client-player.jsp`, it connects to the WebSocket hub and registers itself with role `TV`. The server automatically assigns it a unique integer `clientId` (e.g. `1`, `2`, `3`, `4`).
2. **On-Screen Identification Badge:**
   In the top-right corner of each TV screen, a glowing badge displays its device name and assigned ID:
   `📺 Living Room TV | ID: #1`
3. **Remote Control Targeting:**
   The React Remote has a **TARGET** dropdown. You can choose:
   - **Specific TV** (e.g., `📺 TV #1 – Living Room TV`): Commands only affect that single TV.
   - **📢 BROADCAST ALL**: Commands change the channel, volume, or power on **all** connected TVs at once.

---

## 🚀 Quick Start (1-Click)

### Step 1: Start the Servers
Double-click:
```bash
start-all.bat
```
*(Or run `run-backend.bat` in one terminal and `run-frontend.bat` in another).*

* **Backend (Tomcat 10):** `http://localhost:8085/tv-simulator/`
* **Frontend Remote:** `http://localhost:5173/`

---

### Step 2: Launch 4 TV Screens + Remote
Double-click the included multi-TV launcher:
```bash
open-multi-tvs.bat
```

This will automatically open 4 named TV client tabs and the remote control in your browser:
* **TV #1:** `http://localhost:8085/tv-simulator/client-player.jsp?name=Living+Room+TV`
* **TV #2:** `http://localhost:8085/tv-simulator/client-player.jsp?name=Bedroom+TV`
* **TV #3:** `http://localhost:8085/tv-simulator/client-player.jsp?name=Kitchen+TV`
* **TV #4:** `http://localhost:8085/tv-simulator/client-player.jsp?name=Sports+Bar+TV`
* **Remote:** `http://localhost:5173/`

> 💡 **Window Layout Tip:** Pull the 4 TV tabs out into separate windows and snap them into a **2×2 grid** on your screen (using Windows Key + Arrow keys). Keep the Remote Control window floating or on the side.

---

## 🎮 How to Test Independent Channel & Video Changes

### Test 1: Control a Single TV (e.g. Living Room TV #1)
1. On the **Remote Control** (`http://localhost:5173`), look at the top LCD panel.
2. Click the **TARGET** dropdown and select:
   `📺 TV #1 – Living Room TV (CH 01)`
3. Press **`CH +`** or click **`0` then `3`** on the numeric keypad.
4. **Observe:**
   - **TV #1** switches to Channel 03 (*SportsPulse HD*) and loads the new video stream.
   - **TV #2, #3, and #4 remain completely untouched** playing their existing channels!
   - TV #1 shows the animated channel banner and audio indicator.

---

### Test 2: Switch to Another TV (e.g. Bedroom TV #2)
1. In the **TARGET** dropdown, switch to:
   `📺 TV #2 – Bedroom TV`
2. Notice the LCD on the remote immediately updates to show TV #2's current channel, volume, and power state!
3. Click channel button **`5`** (*KidsZone*).
4. **Observe:** Only TV #2 updates to Channel 05, while TV #1 and the others keep playing their respective channels.

---

### Test 3: Broadcast to All TVs Simultaneously
1. In the **TARGET** dropdown, select:
   `📢 BROADCAST ALL (4 TVs)`
2. Press channel button **`2`** (*Galaxy Cinema*).
3. **Observe:**
   - All 4 TVs immediately switch to Channel 02 simultaneously!
   - All 4 screens show the *Galaxy Cinema* broadcast banner.

---

### Test 4: Individual Volume and Power Off
1. Set TARGET to `📺 TV #3 – Kitchen TV`.
2. Click the red **POWER** button.
3. **Observe:**
   - TV #3 powers off, showing the CRT power-off animation and standby icon (`⏻`).
   - The other 3 TVs remain powered on and playing video normally.
4. Click **POWER** again on the remote to turn TV #3 back on.

---

## 📺 Channel Catalog Reference

| Channel # | Channel Name | Genre | Stream Type |
| :---: | :--- | :--- | :--- |
| **01** | NovaStar News | News | Live HLS (.m3u8) |
| **02** | Galaxy Cinema | Movies | MP4 Video |
| **03** | SportsPulse HD | Sports | MP4 Video |
| **04** | DocuWorld | Documentary | MP4 Video |
| **05** | KidsZone | Kids | MP4 Video |
| **06** | MusicBox | Music | MP4 Video |
| **07** | TechVision | Technology | MP4 Video |
| **08** | FoodieTV | Lifestyle | MP4 Video |
| **09** | HealthFirst | Health | MP4 Video |
| **10** | NightOwl Classic | Classic | MP4 Video |

---

## 🛠️ Troubleshooting & Tips

* **Browsers and Audio Autoplay:**
  Modern browsers (Chrome, Edge, Firefox) mute videos by default on page load until user interaction. To hear audio on a TV window, click anywhere on that TV window or unmute it using the Remote (`MUTE` button).
* **Connection Dot is Red / Offline:**
  Ensure Tomcat 10 is running on port 8085. You can check status anytime at:
  `http://localhost:8085/tv-simulator/api/status`
* **Custom TV Names:**
  You can open a TV client with any custom name simply by adding `?name=YourName` to the URL:
  `http://localhost:8085/tv-simulator/client-player.jsp?name=Patio+Screen`
