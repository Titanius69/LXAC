# LXAC-Velocity

Velocity proxy plugin for LXAC – receives flags from the backend and kicks players network-wide.

## Features
- Listens on plugin messaging channel `lxac:flag`
- When a FLAG message arrives it:
  - Logs which **server** the flag originated from
  - Kicks the player with the hardcoded message **Unfair Advantage**
- Works together with the backend LXAC plugin’s Velocity bridge

## Requirements
- Velocity 3.3+

## Installation
1. Build (`mvn clean package`)
2. Put the jar in Velocity’s `plugins/` folder
3. Make sure the backend has `velocity-bridge: true` and channel `lxac:flag`
4. Restart the proxy

## Package
`com.luminex_studios.lxac.velocity`
