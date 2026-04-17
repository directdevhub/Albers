# ALBERS Tasks

## Phase 1
- Set up package structure
- Add ViewBinding
- Create base activities/fragments
- Add Bluetooth permissions

## Phase 2
- Implement BLE scanner
- Connect to BLE device
- Discover GATT services
- Prepare characteristic references

## Phase 3
- Implement parsers for ADC_SYS and Timer_SYS
- Add repository layer
- Expose device state to ViewModels

## Phase 4 – Screen Layout Setup

### Goal
Create the XML screen layouts for the ALBERS Android app using the existing design resources from Phase 3.

### Requirements
- Use XML layouts only
- Do not use Jetpack Compose
- Use ViewBinding-friendly IDs
- Keep layouts modular and production-ready
- Reuse Phase 3 colors, drawables, and backgrounds
- Use placeholder icons/text where final assets are not available
- Match the ALBERS design as closely as possible

### Screens to Create

#### 1. Splash Screen
- Create `fragment_splash.xml`
- Full-screen dark background
- Centered ALBERS logo
- No controls

#### 2. Start / Connect Screen
- Create `fragment_connect.xml`
- Dark background
- Top app title/logo
- Green power button
- Red power button
- Countdown/timer area disabled by default
- Timer override button disabled by default
- System Status icon disabled by default
- Settings icon disabled by default
- Rinse/Sanitize icon disabled by default
- Help button enabled
- Layout must support a disconnected state

#### 3. Dashboard Screen
- Create `fragment_dashboard.xml`
- Dark background
- Top branding/title
- Green power button with ON label
- Red power button
- Countdown timer text
- “Automatic Pump Cycle Countdown” label
- “Timer Override” label
- PUMP NOW button
- Bottom action row:
  - System Status
  - Settings
  - Rinse/Sanitize
  - Help
- Layout must support:
  - normal state
  - pumping state
  - warning state

#### 4. System Status Screen
- Create `fragment_system_status.xml`
- Show title
- Show status icon area
- Show Pump 1 status
- Show Pump 2 status
- Show Battery percentage
- Show error/status message area
- Help button
- Back button

#### 5. Settings Screen
- Create `fragment_settings.xml`
- Show title
- Show automatic pump cycle interval section
- Buttons/options for:
  - 60
  - 90
  - 120
- Show buttons for:
  - Notifications
  - Voice Control
  - Security/Privacy
- Show System Status button
- Help button
- Back button

#### 6. Rinse / Sanitize Screen
- Create `fragment_rinse.xml`
- Show title
- Show rinse/sanitize label
- START button
- 60-second timer area
- Emergency STOP button
- System Status button
- Help button
- Back button
- Layout must support:
  - enabled state
  - disabled state when both pumps fail

#### 7. Notifications Screen
- Create `fragment_notifications.xml`
- Show title
- RecyclerView or placeholder list container
- Notification item card design placeholder
- Back button

#### 8. Help Screen
- Create `fragment_help.xml`
- Show title
- Scrollable content area
- Placeholder troubleshooting/help text
- Back button

### Supporting Layouts / Components
Create reusable include layouts if useful:
- top title/header
- bottom action row
- power button row
- status card block

### IDs and Structure
- Use clear IDs for all interactive components
- IDs should be ready for ViewBinding and future ViewModel integration

### Output
- All XML layout files
- Any reusable include layouts
- Any additional small drawable/layout resources needed for screen structure
- Keep everything ready for Phase 5 logic integration

## Phase 5
- Wire screen navigation for Phase 4 layouts
- Connect screen state to ViewModels/repositories
- Bind parsed BLE data into dashboard, system status, settings, rinse/sanitize, notifications, and help flows
- Add interaction logic for PUMP NOW, STOP, interval selection, rinse start, emergency stop, Back, Help, and bottom action row controls

## Phase 7 – Fault Handling, Alerts, Reconnection, and Final UI Polish

### Goal
Implement robust fault handling, warning presentation, reconnection flow, notification behavior, and final UI polish for the ALBERS Android app.

### Implemented Scope
- Added repository-backed app state for device status, fault summary, loading/error state, and notification history.
- Added safe BLE parsers for ADC_SYS, Timer_SYS, and Saved diagnostic payloads.
- Added app-side fault interpretation for:
  - one pump failed
  - both pumps failed
  - pressure sensor fault
  - low battery at 10% or less
  - critical battery at 5% or less
  - emergency battery active
  - connection lost / reconnecting
  - battery failure
  - unknown fallback
- Added dashboard state binding for nominal, warning, critical, reconnecting, pumping, disabled pump action, and disabled rinse action states.
- Added System Status binding for pump, battery, battery mode, pressure, and readable fallback values.
- Added Notifications binding for in-app alert/history items.
- Added Help binding for fault-aware troubleshooting guidance.
- Added Rinse/Sanitize availability binding so both-pumps-failed disables rinse start.
- Added bounded BLE reconnect attempts and reconnecting UI state.
- Added local notification helper for critical alerts when notification permission is granted.
- Added readable logging and malformed-payload safeguards.

### Remaining Hardware-Dependent Work
- Confirm whether Android exposes only the documented short UUIDs or expanded 128-bit UUIDs on all target phones.
- Confirm whether firmware requires a separate app-level password characteristic beyond Android passkey bonding.
- Confirm Saved diagnostic entry length if firmware changes; current app uses 44 bytes because ADC_SYS is 11 floats.
- Confirm final pressure fault range and battery type/voltage mapping.
- Connect final PUMP NOW, STOP, rinse, and emergency stop command payloads to the Command characteristic.

### BLE Authentication Handling
- GATT status 5 is handled as Bluetooth authentication/bonding failure.
- The app starts bonding before GATT when the selected ALBERS device is not already bonded.
- If status 5 occurs after bonding, the UI surfaces a readable recovery message instructing the user to forget/unpair `Albers_BLE_BAL3`, pair again with PIN `333333`, and retry.
- Firmware docs confirm the ALBERS GATT service uses short service UUID `0x000F` and characteristics `0x001F`, `0x002F`, `0x003F`, `0x004F`, and `0x005F`.
- The app now auto-reads `ADC_SYS`, `Timer_SYS`, and `Saved` after GATT service discovery.
- `Timer_SYS` is parsed as little-endian 16-bit values (`EE 01 01 00` = elapsed `494`, pump active).
