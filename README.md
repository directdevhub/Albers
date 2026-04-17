# ALBERS Android App

ALBERS is a native Android application built in Kotlin for communicating with a medical pumping hardware device over Bluetooth Low Energy (BLE).

The app is responsible for:
- connecting to the ALBERS hardware board
- reading device status and sensor values
- sending pump and cleaning commands
- displaying battery, pump, pressure, and fault information
- allowing users to control and monitor the device from a mobile phone

## Project Type

- **Platform:** Android
- **Language:** Kotlin
- **UI:** XML Layouts
- **Architecture:** MVVM
- **Communication:** Bluetooth Low Energy (BLE / GATT)

## Project Overview

The ALBERS system is designed to automate fluid transfer in a medical use case.  
The Android app acts as the user interface and control layer for the ALBERS prototype board.

The hardware:
- is powered by battery or external power
- exposes BLE GATT characteristics
- provides battery, pressure, timer, and pump current data
- receives commands from the mobile app

The app will allow the user to:
- connect to the device
- monitor system state
- view countdown timers
- start pump operations
- start rinse/sanitize cycle
- detect warnings and faults
- review notifications and help information

## Main Features

### 1. Device Connection
- Scan and connect to ALBERS BLE device
- Authenticate using device password
- Handle disconnect and reconnect flow

### 2. Dashboard
- Show ON/OFF device state
- Show automatic pump cycle countdown
- Manual override using **PUMP NOW**
- Show pumping state in real time

### 3. System Status
- Show pump 1 and pump 2 condition
- Show battery percentage
- Show pressure and current-related warnings
- Show fault conditions

### 4. Settings
- Select automatic pump cycle interval
- Notifications
- Voice control
- Security / privacy

### 5. Rinse / Sanitize
- Start 60-second cleaning cycle
- Emergency stop action
- Disable action when both pumps are unavailable

### 6. Notifications / Help
- Show warning and activity history
- Display troubleshooting guidance

## BLE Integration Summary

The hardware communicates using BLE GATT.

Known characteristics from the current documentation:

- Service `0x000F` - ALBERS custom GATT service
- `ADC_SYS` `0x001F` - Read system values
- `Timer_SYS` `0x002F` - Read timer and pump state
- `Fecha_SYS` `0x003F` - Write date/time
- `Command` `0x004F` - Write commands
- `Saved` `0x005F` - Read stored diagnostics during disconnection

### Example hardware values
The device provides:
- date/time
- battery level
- battery type
- pump 1 current
- pump 2 current
- pressure

### Important note
Some BLE data is transmitted in swapped byte order and must be parsed carefully in the app.
`Timer_SYS` is little-endian: `EE 01 01 00` means elapsed time `494` and pump active.

## Tech Stack

- Kotlin
- Android SDK
- XML Layouts
- ViewBinding
- MVVM
- StateFlow / LiveData
- BluetoothGatt API

## Current UI Layout Scope

Phase 4 establishes the XML screen layout foundation for the ALBERS app. The app uses XML layouts only, with ViewBinding-friendly IDs and reusable resources for the dark ALBERS visual design.

Implemented layout files:
- `fragment_splash.xml` - full-screen dark splash with centered ALBERS logo and no controls
- `fragment_connect.xml` - disconnected Start / Connect screen with green power action, red inactive power control, disabled countdown/timer override area, disabled System Status / Settings / Rinse actions, and enabled Help access
- `fragment_dashboard.xml` - main dashboard with ON state, red stop control, countdown timer, Automatic Pump Cycle Countdown label, Timer Override label, PUMP NOW action, bottom action row, and placeholders for pumping and warning states
- `fragment_system_status.xml` - system status layout with pump 1, pump 2, battery, status icon, message area, Back, and Help actions
- `fragment_settings.xml` - settings layout with 60 / 90 / 120 minute interval choices, notifications, voice control, security/privacy, System Status, Back, and Help actions
- `fragment_rinse.xml` - rinse/sanitize layout with START, 60-second timer, Emergency STOP, System Status, Back, Help, and disabled-state messaging
- `fragment_notifications.xml` - notifications layout with warning card and placeholder list container
- `fragment_help.xml` - scrollable troubleshooting/help layout with Back action

Reusable layout components:
- `include_albers_header.xml` - shared ALBERS logo/title header
- `include_bottom_nav.xml` - enabled bottom action row
- `include_bottom_nav_disconnected.xml` - disconnected bottom action row with only Help enabled

## Fault Handling And Alerts

Phase 7 adds an app-side fault-state layer on top of parsed BLE values. Hardware values are interpreted through a shared repository so dashboard, system status, notifications, help, and rinse flows stay consistent.

Supported app-side states:
- one pump failed
- both pumps failed
- pressure sensor fault
- low battery at 10% or less
- critical battery at 5% or less
- emergency battery active
- connection lost / reconnecting
- battery failure
- unknown fault fallback

Current assumptions:
- Pump failure is inferred from pump current below `0.4A`, based on project context dry-run guidance.
- Pressure sensor fault is treated as a value outside `0..1500 hPa` until final product limits are confirmed.
- Battery type mapping is provisional: `0 = main`, `1 = emergency`, `2 = failed`.
- Saved diagnostics parsing supports multiple 44-byte ADC_SYS-style entries. The firmware document also mentions 40-byte entries in one paragraph, but that conflicts with the documented 11 floats and the nRF screenshots, so the app uses 44 bytes.
- After GATT service discovery, the app looks for ALBERS short UUIDs and reads `ADC_SYS`, `Timer_SYS`, and `Saved`.

Critical alerts can create phone-side notifications when notification permission is granted. Repeated states are de-duplicated so the notification list does not spam the same unchanged fault.

### BLE Pairing Note

`Albers_BLE_BAL3` pairs with PIN `333333`. If Android reports GATT status `5`, the app treats it as a Bluetooth authentication/bonding failure: it bonds before opening GATT when possible and shows a recovery message asking the user to forget/unpair the device, pair again with PIN `333333`, and retry.

## Suggested Package Structure

```text
com.albers.app
├── ui
│   ├── splash
│   ├── connect
│   ├── dashboard
│   ├── systemstatus
│   ├── settings
│   ├── rinse
│   ├── notifications
│   └── help
├── ble
├── data
│   ├── model
│   ├── parser
│   └── repository
├── utils
└── viewmodel
