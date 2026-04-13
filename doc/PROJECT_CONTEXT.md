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

## BLE GATT Characteristics
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
- First 2 bytes: elapsed time
- Last 2 bytes: pump status

Pump status:
- 1 = active
- 0 = inactive

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
Each entry is 44 bytes and follows the same format as ADC_SYS.

## Hardware Logic Notes
- Normal pumping current is about 0.9A
- Dry-run detection occurs when current drops below 0.4A
- Hardware automatically stops pumps when liquid is depleted
- status_var[8] and status_var[9] are pump current readings
- wait_timer = cycle time set by user
- pump_timer = active pump countdown starting at 30

## Current Unknowns
- Full BLE service UUID
- Exact password exchange implementation
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