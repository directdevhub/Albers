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

- `ADC_SYS` - Read system values
- `Timer_SYS` - Read timer and pump state
- `Fecha_SYS` - Write date/time
- `Command` - Write commands
- `Saved` - Read stored diagnostics during disconnection

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

## Tech Stack

- Kotlin
- Android SDK
- XML Layouts
- ViewBinding
- MVVM
- StateFlow / LiveData
- BluetoothGatt API

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
