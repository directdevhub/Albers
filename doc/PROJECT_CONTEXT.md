# ALBERS Project Context

## Overview
ALBERS is a native Android application built in Kotlin using XML layouts.  
The app connects to a medical hardware prototype over Bluetooth Low Energy (BLE) and allows the user to monitor system status and control pump-related operations.

## Purpose
The ALBERS system is used to automate fluid transfer from a medical leg bag into a reservoir using pumps.  
The mobile app acts as the control and monitoring interface.

## App Stack
- Android
- Kotlin
- XML layouts
- MVVM architecture
- BLE GATT communication

## Hardware Summary
- Hardware is a functional prototype board
- MCU: STM32WB15
- Firmware language: C++
- Communication: BLE
- Test password: 333333
- Board is discoverable once powered
- Power source: battery or external 12V

## Main App Screens
- Splash screen
- Start / connect screen
- Main dashboard
- System status screen
- Settings screen
- Rinse / sanitize screen
- Notifications
- Help
- Privacy / security

## Phase 4 Screen Layout Setup
Phase 4 creates the XML-only screen layout foundation using the established dark ALBERS design resources. Layouts must remain modular, ViewBinding-friendly, and ready for Phase 5 logic integration.

### Layout Requirements
- XML layouts only; Jetpack Compose is not used
- Full dark screen backgrounds using existing Phase 3 resources
- Light text and high-contrast green/red/yellow state colors
- Reusable include layouts where useful
- Clear IDs for interactive controls and future ViewModel binding
- Placeholder icons/text are acceptable where final assets are not available

### Phase 4 Screens
- `fragment_splash.xml`: full-screen dark splash, centered ALBERS logo, no controls
- `fragment_connect.xml`: Start / Connect screen with top branding, green power action, red inactive power control, disabled countdown/timer override, disconnected-state bottom actions, and Help enabled
- `fragment_dashboard.xml`: dashboard with ON state, red stop control, countdown timer, Automatic Pump Cycle Countdown label, Timer Override label, PUMP NOW button, bottom action row, pumping-state placeholder, and warning-state placeholder
- `fragment_system_status.xml`: pump 1, pump 2, battery percentage, status icon area, error/status message area, Back, and Help
- `fragment_settings.xml`: automatic pump interval options for 60 / 90 / 120 minutes, Notifications, Voice Control, Security/Privacy, System Status, Back, and Help
- `fragment_rinse.xml`: rinse/sanitize label, START, 60-second timer, Emergency STOP, System Status, Back, Help, and disabled-state messaging for pump failure
- `fragment_notifications.xml`: title, warning card, placeholder list container, notification item card placeholders, and Back
- `fragment_help.xml`: title, scrollable troubleshooting/help text, and Back

### Reusable Layout Components
- `include_albers_header.xml`: shared logo/title header
- `include_bottom_nav.xml`: enabled bottom action row
- `include_bottom_nav_disconnected.xml`: disconnected bottom action row with only Help enabled

## Main Dashboard Behavior
- Green button starts the device session
- Main screen shows ON state
- Countdown timer shows automatic pump cycle interval
- "PUMP NOW" triggers immediate pump action
- During pumping, UI shows flashing "PUMPING"
- Red button stops pumping

## Settings
- Pump interval options: 60 / 90 / 120 minutes
- Notifications
- Voice control
- Security / privacy

## Rinse / Sanitize
- Start button runs pumps for 60 seconds
- Emergency stop cancels cycle and resets timer
- Rinse/Sanitize should be disabled if both pumps fail

## Phase 7 Fault Handling Context
The app maintains a repository-backed fault state derived from parsed BLE data where possible. The repository exposes one shared app state for ViewModels and screens.

### Fault States
- One pump failed
- Both pumps failed
- Pressure sensor fault
- Low battery at 10% or less
- Critical battery at 5% or less
- Emergency battery active
- Connection lost / reconnecting
- Battery failure
- Unknown fault fallback

### UI Behavior
- Dashboard shows a nominal/thumbs-up state when no faults exist.
- Dashboard shows hazard warnings for pump and pressure issues.
- Dashboard shows low-battery and critical-battery states based on battery percentage.
- Critical battery and battery failure can trigger phone-side alerts when notification permission is available.
- Automatic pump controls are disabled for both pumps failed, pressure sensor fault, critical battery, battery failure, or disconnected state.
- Rinse/Sanitize remains available when at least one pump is operable and is disabled when both pumps fail.
- System Status displays pump, battery, battery mode, pressure, and readable fallback labels when data is unavailable.
- Notifications displays in-app history for pump cycle completion, low battery, critical battery, reconnect success, pump errors, battery failure, connection loss, and stored diagnostics.
- Help remains available while disconnected and includes troubleshooting guidance for pairing, battery, pump, and emergency battery states.

### Implementation Assumptions
- Pump current below 0.4A indicates a pump issue based on project dry-run notes.
- Pressure range validation currently uses 0..1500 hPa until final hardware limits are confirmed.
- Battery type mapping is provisional: 0 main, 1 emergency, 2 failed.
- Saved diagnostics parsing supports multiple 44-byte entries that reuse ADC_SYS decoding rules.
- Full Saved characteristic reads require confirmed service and characteristic UUIDs.
- Android GATT status 5 is treated as a Bluetooth authentication/bonding failure. The app should bond before opening GATT and, if status 5 still occurs, ask the user to forget/unpair `Albers_BLE_BAL3`, pair again with PIN `333333`, and retry.

## BLE GATT Characteristics
- ALBERS custom service (0x000F) - PRIMARY SERVICE
- ADC_SYS (0x001F) - READ
- Timer_SYS (0x002F) - READ
- Fecha_SYS (0x003F) - WRITE
- Command (0x004F) - WRITE
- Saved (0x005F) - READ

## ADC_SYS Data
ADC_SYS returns 11 floating-point values, 4 bytes each:
- [0] Year
- [1] Month
- [2] Day
- [3] Hour
- [4] Minutes
- [5] Seconds
- [6] Battery level (1-100%)
- [7] Battery type
- [8] Pump 1 current
- [9] Pump 2 current
- [10] Pressure in hPa

Important:
Each float is transmitted with swapped byte order and must be corrected before parsing.

## Timer_SYS Data
4 bytes total:
- First 2 bytes: elapsed time, LSB first
- Last 2 bytes: pump status, LSB first

Pump status:
- 1 = active
- 0 = inactive

Example:
- `EE 01 01 00` means elapsed time `494` and pump active.

## Fecha_SYS
Used to write:
Year / Month / Day / Hour / Minute / Second

## Command Values
- 00 = cleaning cycle
- 01 = pumping case 1
- 02 = pumping case 2
- 03 = pumping case 3

## Saved Characteristic
Returns stored diagnostic entries recorded during BLE disconnection.  
Each entry is treated as 44 bytes and follows the same format as ADC_SYS.
Note: the firmware document contains one inconsistent line saying 40-byte entries, but it also says 11 floats and shows 44-byte ADC_SYS payloads. The app uses 44 bytes unless firmware changes confirm otherwise.

## Hardware Logic Notes
- Normal pumping current is about 0.9A
- Dry-run detection occurs when current drops below 0.4A
- Hardware automatically stops pumps when liquid is depleted
- status_var[8] and status_var[9] are pump current readings
- wait_timer = cycle time set by user
- pump_timer = active pump countdown starting at 30

## Current Unknowns
- Full 128-bit BLE service/characteristic UUID expansion if Android exposes more than the documented short UUIDs
- Exact app-level password characteristic if firmware requires more than Android passkey bonding
- Exact meaning of pump command cases 1, 2, 3
- Exact command or mechanism for stop/off
- Whether updates are polling-based or notification-based

## Development Goal
Build the Android app in phases:
1. Base project setup
2. BLE permissions and scan
3. BLE connection and GATT discovery
4. Authentication flow
5. Characteristic parsing
6. Dashboard UI integration
7. System status and settings
8. Rinse/Sanitize flow
9. Notifications and fault handling
